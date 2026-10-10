import { useMutation, useQueryClient } from "@tanstack/react-query";
import { communityService } from "../api/communityService";

export function useCommunityActions(communityId: number) {
  const queryClient = useQueryClient();

  // Join Mutation
  const joinMutation = useMutation({
    mutationFn: () => communityService.joinCommunity(communityId),
    onSuccess: () => {
      // Refresh current community state
      queryClient.invalidateQueries({ queryKey: ["community", communityId] });
      // Refresh list of all communities to show membership status
      queryClient.invalidateQueries({ queryKey: ["communities"] });
      // Invalidate my membership cache
      queryClient.invalidateQueries({ queryKey: ["my-communities"] });
    },
  });

  // Leave Mutation
  const leaveMutation = useMutation({
    mutationFn: () => communityService.leaveCommunity(communityId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["community", communityId] });
      queryClient.invalidateQueries({ queryKey: ["communities"] });
      queryClient.invalidateQueries({ queryKey: ["my-communities"] });
    },
  });

  return {
    join: joinMutation.mutate,
    isJoining: joinMutation.isPending,
    leave: leaveMutation.mutate,
    isLeaving: leaveMutation.isPending,
  };
}
