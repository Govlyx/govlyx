import axiosInstance from "./axiosConfig";

export const postService = {
  likePost: async (postId: number, postType: "posts" | "social-posts") => {
    const response = await axiosInstance.post(`/api/interactions/${postType}/${postId}/like`);
    return response.data;
  },

  savePost: async (postId: number, postType: "posts" | "social-posts") => {
    const response = await axiosInstance.post(`/api/interactions/${postType}/${postId}/save`);
    return response.data;
  },

  deletePost: async (postId: number, postType: "posts" | "social-posts") => {
    await axiosInstance.delete(`/api/${postType}/${postId}`);
  },

  resolveIssue: async (postId: number, message: string) => {
    try {
      const response = await axiosInstance.put(
        `/api/posts/${postId}/resolution?isResolved=true&updateMessage=${encodeURIComponent(message)}`
      );
      return response.data;
    } catch {
      const response = await axiosInstance.post(
        `/api/posts/${postId}/resolution?isResolved=true&updateMessage=${encodeURIComponent(message)}`
      );
      return response.data;
    }
  },

  getPostCounts: async (postId: number, postType: "posts" | "social-posts") => {
    const response = await axiosInstance.get(`/api/interactions/${postType}/${postId}/counts`);
    return response.data;
  },

  getPostById: async (postId: number, postType: "posts" | "social-posts") => {
    const response = await axiosInstance.get(`/api/${postType}/${postId}`);
    return response.data;
  },

  voteInPoll: async (pollId: number, optionIds: number[]) => {
    const response = await axiosInstance.post(`/api/polls/${pollId}/vote`, {
      optionIds,
    });
    return response.data;
  },

  createPost: async (payload: { content: string; targetPincode: string; forceSubmit?: boolean }, idempotencyKey?: string) => {
    const response = await axiosInstance.post("/api/posts", payload, {
      headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
    });
    return response.data;
  },

  createPostWithMedia: async (payload: { content: string; targetPincode: string; media: File; forceSubmit?: boolean }, idempotencyKey?: string) => {
    const form = new FormData();
    form.append("content", payload.content);
    form.append("targetPincode", payload.targetPincode);
    form.append("media", payload.media);
    if (payload.forceSubmit !== undefined) {
      form.append("forceSubmit", payload.forceSubmit ? "true" : "false");
    }
    const response = await axiosInstance.post("/api/posts/with-media", form, {
      headers: {
        "Content-Type": "multipart/form-data",
        ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
      },
    });
    return response.data;
  },

  createSocialPost: async (payload: { content: string; communityId?: number; category?: string }, idempotencyKey?: string) => {
    const response = await axiosInstance.post("/api/social-posts/text", {
      content: payload.content,
      allowComments: true,
      communityId: payload.communityId,
      category: payload.category || "GENERAL",
    }, {
      headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
    });
    return response.data;
  },

  createSocialPostWithMedia: async (payload: { content: string; files: File[]; communityId?: number; category?: string }, idempotencyKey?: string) => {
    const form = new FormData();
    form.append(
      "post",
      new Blob([JSON.stringify({ content: payload.content, allowComments: true, communityId: payload.communityId, category: payload.category || "GENERAL" })], {
        type: "application/json",
      })
    );
    payload.files.forEach((f) => form.append("media", f));
    const response = await axiosInstance.post("/api/social-posts/with-media", form, {
      headers: {
        "Content-Type": "multipart/form-data",
        ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
      },
    });
    return response.data;
  },

  createPollPost: async (payload: any, idempotencyKey?: string) => {
    const response = await axiosInstance.post("/api/polls/create", payload, {
      headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
    });
    return response.data;
  },

  createPollPostWithMedia: async (payload: { poll: any; media: File[] }, idempotencyKey?: string) => {
    const form = new FormData();
    form.append(
      "poll",
      new Blob([JSON.stringify(payload.poll)], { type: "application/json" })
    );
    payload.media.forEach((f) => form.append("media", f));
    const response = await axiosInstance.post("/api/polls/create-with-media", form, {
      headers: {
        "Content-Type": "multipart/form-data",
        ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
      },
    });
    return response.data;
  },
};