import { useMutation, useQueryClient } from '@tanstack/react-query';
import { postService } from '../api/postService';
import { communityService } from '../api/communityService';

export function usePostInteractions(
  postId: number,
  postType: 'posts' | 'social-posts',
) {
  const queryClient = useQueryClient();

  // Like Mutation
  const likeMutation = useMutation({
    mutationFn: () => postService.likePost(postId, postType),
    onMutate: async () => {
      // Cancel any outgoing refetches
      await queryClient.cancelQueries({ queryKey: ['post', postId] });
      await queryClient.cancelQueries({ queryKey: ['posts'] });
      await queryClient.cancelQueries({ queryKey: ['social-posts'] });

      // Snapshot previous value
      // In a real app, we'd update the specific post in the list cache
      // For now, we'll return a rollback function or just handle it in the component
      return { prevPosts: queryClient.getQueryData(['posts']) };
    },
    onSuccess: () => {
      // Invalidate queries to sync state across the app
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      queryClient.invalidateQueries({ queryKey: ['social-posts'] });
      queryClient.invalidateQueries({ queryKey: ['post', postId] });
    },
    onError: () => {
      // Rollback if needed
    },
  });

  // Save Mutation
  const saveMutation = useMutation({
    mutationFn: () => postService.savePost(postId, postType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      queryClient.invalidateQueries({ queryKey: ['social-posts'] });
      queryClient.invalidateQueries({ queryKey: ['saved-posts'] });
    },
  });

  // Delete Mutation
  const deleteMutation = useMutation({
    mutationFn: () => postService.deletePost(postId, postType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['posts'] });
      queryClient.invalidateQueries({ queryKey: ['social-posts'] });
    },
  });

  return {
    like: likeMutation.mutate,
    isLiking: likeMutation.isPending,
    save: saveMutation.mutate,
    isSaving: saveMutation.isPending,
    remove: deleteMutation.mutate,
    isDeleting: deleteMutation.isPending,
  };
}

export function useCreatePost() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      isReportingIssue,
      content,
      targetPincode,
      files,
      communityId,
      forceSubmit,
      idempotencyKey,
      category,
    }: {
      isReportingIssue: boolean;
      content: string;
      targetPincode: string;
      files: File[];
      communityId?: number;
      forceSubmit?: boolean;
      idempotencyKey: string;
      category?: string;
      currentUser: {
        username: string;
        actualUsername?: string;
        profileImage: string | null;
      };
    }) => {
      if (isReportingIssue) {
        if (files.length > 0) {
          return postService.createPostWithMedia(
            { content, targetPincode, media: files[0], forceSubmit },
            idempotencyKey,
          );
        } else {
          return postService.createPost(
            { content, targetPincode, forceSubmit },
            idempotencyKey,
          );
        }
      } else {
        if (files.length > 0) {
          return postService.createSocialPostWithMedia(
            {
              content,
              files,
              communityId,
              category,
              targetPincode: targetPincode || undefined,
            },
            idempotencyKey,
          );
        } else {
          return postService.createSocialPost(
            {
              content,
              communityId,
              category,
              targetPincode: targetPincode || undefined,
            },
            idempotencyKey,
          );
        }
      }
    },
    onMutate: async (variables) => {
      await queryClient.cancelQueries({ queryKey: ['feed'] });

      const optimisticId = -Date.now();
      const optimisticPost: any = {
        id: optimisticId,
        variant: variables.isReportingIssue ? 'issue' : 'social',
        content: variables.content,
        createdAt: new Date().toISOString(),
        username:
          variables.currentUser.actualUsername ??
          variables.currentUser.username,
        actualUsername: variables.currentUser.actualUsername,
        userDisplayName:
          variables.currentUser.actualUsername ??
          variables.currentUser.username,
        userProfileImage: variables.currentUser.profileImage,
        status: 'ACTIVE',
        likeCount: 0,
        dislikeCount: 0,
        commentCount: 0,
        shareCount: 0,
        isPendingSync: true,
        idempotencyKey: variables.idempotencyKey,
        mediaUrls:
          variables.files.length > 0
            ? [URL.createObjectURL(variables.files[0])]
            : [],
        targetPincodes: variables.targetPincode
          ? [variables.targetPincode]
          : [],
        userPincode: variables.targetPincode || undefined,
        broadcastScope: variables.isReportingIssue ? 'AREA' : undefined,
        category: variables.category || 'GENERAL',
      };

      const prevFeeds: [any, any][] = [];
      queryClient
        .getQueryCache()
        .getAll()
        .forEach((query: any) => {
          const key = query.queryKey;
          if (key[0] === 'feed') {
            const previousData = queryClient.getQueryData(key);
            prevFeeds.push([key, previousData]);
            queryClient.setQueryData(key, (oldData: any) => {
              if (!oldData) {
                return {
                  pages: [
                    {
                      posts: [optimisticPost],
                      hasMore: false,
                      nextCursor: null,
                    },
                  ],
                  pageParams: [null],
                };
              }
              const firstPage = oldData.pages[0];
              if (!firstPage) return oldData;
              return {
                ...oldData,
                pages: [
                  { ...firstPage, posts: [optimisticPost, ...firstPage.posts] },
                  ...oldData.pages.slice(1),
                ],
              };
            });
          }
        });

      return { prevFeeds, optimisticId };
    },
    onSuccess: (data, _variables, context) => {
      const syncedPost = data.data ?? data;
      queryClient
        .getQueryCache()
        .getAll()
        .forEach((query: any) => {
          const key = query.queryKey;
          if (key[0] === 'feed') {
            queryClient.setQueryData(key, (oldData: any) => {
              if (!oldData) return oldData;
              return {
                ...oldData,
                pages: oldData.pages.map((page: any) => ({
                  ...page,
                  posts: page.posts.map((p: any) =>
                    p.id === context?.optimisticId
                      ? { ...syncedPost, isPendingSync: false }
                      : p,
                  ),
                })),
              };
            });
          }
        });
    },
    onError: (_err, _variables, context) => {
      if (context?.prevFeeds) {
        context.prevFeeds.forEach(([key, val]: any) => {
          queryClient.setQueryData(key, val);
        });
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['feed'] });
    },
  });
}

export function useCreatePoll() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      payload,
      files,
      idempotencyKey,
    }: {
      payload: any;
      files: File[];
      idempotencyKey: string;
      currentUser: {
        username: string;
        actualUsername?: string;
        profileImage: string | null;
      };
    }) => {
      if (files.length > 0) {
        return postService.createPollPostWithMedia(
          { poll: payload, media: files },
          idempotencyKey,
        );
      } else {
        return postService.createPollPost(payload, idempotencyKey);
      }
    },
    onMutate: async (variables) => {
      await queryClient.cancelQueries({ queryKey: ['feed'] });

      const optimisticId = -Date.now();
      const optimisticPost: any = {
        id: optimisticId,
        variant: 'poll',
        content: variables.payload.question,
        createdAt: new Date().toISOString(),
        username:
          variables.currentUser.actualUsername ??
          variables.currentUser.username,
        actualUsername: variables.currentUser.actualUsername,
        userDisplayName:
          variables.currentUser.actualUsername ??
          variables.currentUser.username,
        userProfileImage: variables.currentUser.profileImage,
        status: 'ACTIVE',
        likeCount: 0,
        dislikeCount: 0,
        commentCount: 0,
        shareCount: 0,
        isPendingSync: true,
        idempotencyKey: variables.idempotencyKey,
        mediaUrls:
          variables.files.length > 0
            ? [URL.createObjectURL(variables.files[0])]
            : [],
        poll: {
          question: variables.payload.question,
          options: variables.payload.options.map((o: string, idx: number) => ({
            id: idx,
            text: o,
            votes: 0,
          })),
          expiresAt:
            variables.payload.expiresIn === 'always' ||
            variables.payload.expiresIn === 'never' ||
            !variables.payload.expiresIn
              ? null
              : new Date(
                  Date.now() +
                    (variables.payload.expiresIn === '1h' ||
                    variables.payload.expiresIn === '1H'
                      ? 3600000
                      : variables.payload.expiresIn === '3d' ||
                          variables.payload.expiresIn === '3D'
                        ? 259200000
                        : variables.payload.expiresIn === '7d' ||
                            variables.payload.expiresIn === '7D'
                          ? 604800000
                          : 86400000),
                ).toISOString(),
          allowMultipleVotes: variables.payload.allowMultipleVotes,
          userVoted: false,
          totalVotes: 0,
        },
      };

      const prevFeeds: [any, any][] = [];
      queryClient
        .getQueryCache()
        .getAll()
        .forEach((query: any) => {
          const key = query.queryKey;
          if (key[0] === 'feed') {
            const previousData = queryClient.getQueryData(key);
            prevFeeds.push([key, previousData]);
            queryClient.setQueryData(key, (oldData: any) => {
              if (!oldData) {
                return {
                  pages: [
                    {
                      posts: [optimisticPost],
                      hasMore: false,
                      nextCursor: null,
                    },
                  ],
                  pageParams: [null],
                };
              }
              const firstPage = oldData.pages[0];
              if (!firstPage) return oldData;
              return {
                ...oldData,
                pages: [
                  { ...firstPage, posts: [optimisticPost, ...firstPage.posts] },
                  ...oldData.pages.slice(1),
                ],
              };
            });
          }
        });

      return { prevFeeds, optimisticId };
    },
    onSuccess: (data, _variables, context) => {
      const syncedPost = data.data ?? data;
      queryClient
        .getQueryCache()
        .getAll()
        .forEach((query: any) => {
          const key = query.queryKey;
          if (key[0] === 'feed') {
            queryClient.setQueryData(key, (oldData: any) => {
              if (!oldData) return oldData;
              return {
                ...oldData,
                pages: oldData.pages.map((page: any) => ({
                  ...page,
                  posts: page.posts.map((p: any) =>
                    p.id === context?.optimisticId
                      ? { ...syncedPost, isPendingSync: false }
                      : p,
                  ),
                })),
              };
            });
          }
        });
    },
    onError: (_err, _variables, context) => {
      if (context?.prevFeeds) {
        context.prevFeeds.forEach(([key, val]: any) => {
          queryClient.setQueryData(key, val);
        });
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['feed'] });
    },
  });
}

export function useCreateComment() {
  return useMutation({
    mutationFn: async ({
      postId,
      postType,
      payload,
      idempotencyKey,
    }: {
      postId: number;
      postType: 'posts' | 'social-posts';
      payload: { text: string; parentCommentId?: number };
      idempotencyKey: string;
    }) => {
      return communityService.createComment(
        postId,
        postType,
        payload,
        idempotencyKey,
      );
    },
  });
}
