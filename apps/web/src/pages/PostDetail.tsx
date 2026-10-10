import React, { useState, useEffect } from "react";
import { useParams, useNavigate, useSearchParams } from "react-router-dom";
import { ChevronLeft, AlertCircle, Loader2, AlertTriangle } from "lucide-react";
import { postService } from "../api/postService";
import PostCard from "../components/post/PostCard";
import type { AnyPost } from "../components/post/PostCard";
import { toPostCardPost } from "../utils/postUtils";
import { useCurrentUser } from "../hooks/useUser";
import { Helmet } from "react-helmet-async";
import { getAuthToken } from "../utils/auth";

const PostDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [post, setPost] = useState<AnyPost | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { data: user } = useCurrentUser({ enabled: !!getAuthToken() });

  const currentUser = user ? {
    id: user.id,
    username: user.actualUsername || user.username,
    role: user.role
  } : undefined;

  useEffect(() => {
    const fetchPost = async () => {
      if (!id) return;
      setLoading(true);
      setError(null);

      const postId = parseInt(id, 10);
      if (isNaN(postId)) {
        setError("Invalid post ID.");
        setLoading(false);
        return;
      }

      const typeParam = searchParams.get("type"); // "posts" or "social-posts"

      try {
        let data;
        if (typeParam === "posts" || typeParam === "social-posts") {
          try {
            data = await postService.getPostById(postId, typeParam);
          } catch (socialErr: any) {
            const status = socialErr.response?.status;
            const msg = socialErr.message;
            if (status === 451 || msg === "451" || msg?.includes("451")) {
              throw socialErr;
            }
            // Fallback to alternate endpoint if explicit type fails with 404
            const alternateType = typeParam === "posts" ? "social-posts" : "posts";
            data = await postService.getPostById(postId, alternateType);
          }
        } else {
          // Fallback to default behavior if no query param type is provided
          try {
            data = await postService.getPostById(postId, "social-posts");
          } catch (socialErr: any) {
            const status = socialErr.response?.status;
            const msg = socialErr.message;
            if (status === 451 || msg === "451" || msg?.includes("451")) {
              throw socialErr;
            }
            // If social-posts fails, fallback to posts endpoint for any non-legal (451) error
            if (status !== 451 && msg !== "451" && !msg?.includes("451")) {
              data = await postService.getPostById(postId, "posts");
            } else {
              throw socialErr;
            }
          }
        }

        // The response might be wrapped in ApiResponse: { success: true, data: { ... } }
        const actualPostData = data?.data || data;
        if (!actualPostData) {
          throw new Error("Post not found.");
        }

        setPost(toPostCardPost(actualPostData));
      } catch (err: any) {
        console.error("Error fetching post:", err);
        const status = err.response?.status;
        const msg = err.message;
        if (status === 451 || msg === "451" || msg?.includes("451")) {
          setError("LEGAL_TAKEDOWN");
        } else {
          setError(err.response?.data?.message || err.message || "Failed to load post.");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchPost();
  }, [id]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
        <p className="text-sm opacity-50 font-medium">Loading post...</p>
      </div>
    );
  }

  if (error === "LEGAL_TAKEDOWN") {
    return (
      <div className="max-w-2xl mx-auto p-6">
        <div className="bg-slate-900/40 border border-slate-800 rounded-3xl p-8 text-center flex flex-col items-center gap-4 backdrop-blur-md">
          <div className="w-16 h-16 bg-slate-800/80 rounded-full flex items-center justify-center text-slate-400">
            <AlertTriangle className="w-8 h-8 text-slate-450" />
          </div>
          <h2 className="text-xl font-bold text-slate-200">Content Unavailable</h2>
          <p className="text-slate-450 max-w-sm text-sm leading-relaxed">
            This content is unavailable in your region due to a legal complaint or copyright infringement claim.
          </p>
          <button
            onClick={() => navigate(-1)}
            className="btn btn-ghost btn-sm text-slate-400 hover:bg-slate-800 border-slate-700 mt-2"
          >
            Go Back
          </button>
        </div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="max-w-2xl mx-auto p-6">
        <div className="bg-red-50 border border-red-200 rounded-2xl p-8 text-center flex flex-col items-center gap-4">
          <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center text-red-600">
            <AlertCircle size={32} />
          </div>
          <h2 className="text-xl font-bold text-red-900">Oops! Something went wrong</h2>
          <p className="text-red-700 max-w-sm">{error || "We couldn't find the post you're looking for."}</p>
          <button
            onClick={() => navigate(-1)}
            className="btn btn-ghost btn-sm text-red-700 hover:bg-red-100 border-red-200"
          >
            Go Back
          </button>
        </div>
      </div>
    );
  }

  const postSnippet = post.content ? (post.content.length > 50 ? post.content.substring(0, 50) + "..." : post.content) : "Civic Issue";
  const postTitle = `${postSnippet} | Govlyx`;
  const postDesc = post.content ? (post.content.length > 150 ? post.content.substring(0, 150) + "..." : post.content) : "Read details on this hyperlocal civic post on Govlyx.";
  const postKeywords = `report civic issue, local complaints, ${(post as any).category || post.variant || "civic issue"}, Govlyx, ${(post as any).targetPincode || (post as any).targetPincodes?.join(", ") || ""}`.trim();
  const canonicalUrl = `https://govlyx.com/post/${post.id}`;
  const ogImage = (post as any).mediaUrls?.[0] || (post as any).mediaUrl || (post as any).imageUrl || "https://govlyx.com/govlyx-og.png";

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "SocialMediaPosting",
    "headline": postSnippet,
    "articleBody": post.content || postDesc,
    "datePublished": (post as any).createdAt || new Date().toISOString(),
    "image": ogImage,
    "author": {
      "@type": "Person",
      "name": (post as any).authorUsername || (post as any).authorName || "Citizen"
    },
    "publisher": {
      "@type": "Organization",
      "name": "Govlyx",
      "url": "https://govlyx.com"
    }
  };

  return (
    <div className="max-w-4xl mx-auto p-4 sm:p-6 space-y-6">
      <Helmet>
        <title>{postTitle}</title>
        <meta name="description" content={postDesc} />
        <meta name="keywords" content={postKeywords} />
        <link rel="canonical" href={canonicalUrl} />
        <meta property="og:title" content={postTitle} />
        <meta property="og:description" content={postDesc} />
        <meta property="og:url" content={canonicalUrl} />
        <meta property="og:site_name" content="Govlyx" />
        <meta property="og:type" content="article" />
        <meta property="og:image" content={ogImage} />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content={postTitle} />
        <meta name="twitter:description" content={postDesc} />
        <meta name="twitter:image" content={ogImage} />
        <script type="application/ld+json">
          {JSON.stringify(jsonLd)}
        </script>
      </Helmet>
      <button
        onClick={() => navigate(-1)}
        className="btn btn-ghost btn-sm gap-2 opacity-50 hover:opacity-100 -ml-2"
      >
        <ChevronLeft size={18} />
        Back
      </button>

      <div className="w-full">
        <PostCard
          post={post}
          currentUser={currentUser}
          onLike={(likedId, liked) => {
            setPost(prev => {
              if (!prev || prev.id !== likedId) return prev;
              const hasDislikeSupport = prev.variant === "issue";
              const isPrevDisliked = hasDislikeSupport && !!(prev as any).isDislikedByCurrentUser;
              return {
                ...prev,
                isLikedByCurrentUser: liked,
                likeCount: prev.likeCount + (liked ? 1 : -1),
                ...(isPrevDisliked && liked && {
                  isDislikedByCurrentUser: false,
                  dislikeCount: Math.max(0, ((prev as any).dislikeCount ?? 0) - 1)
                })
              } as any;
            });
          }}
          onDislike={(dislikedId, disliked) => {
            setPost(prev => {
              if (!prev || prev.id !== dislikedId) return prev;
              const isPrevLiked = !!prev.isLikedByCurrentUser;
              return {
                ...prev,
                isDislikedByCurrentUser: disliked,
                dislikeCount: ((prev as any).dislikeCount ?? 0) + (disliked ? 1 : -1),
                ...(isPrevLiked && disliked && {
                  isLikedByCurrentUser: false,
                  likeCount: Math.max(0, prev.likeCount - 1)
                })
              } as any;
            });
          }}
          onSave={(savedId, saved) => {
            setPost(prev => prev && prev.id === savedId ? { ...prev, isSaved: saved, isSavedByCurrentUser: saved } as any : prev);
          }}
          onShare={(shareId) => {
            setPost(prev => prev && prev.id === shareId ? { ...prev, shareCount: (prev.shareCount ?? 0) + 1 } as any : prev);
          }}
          onDelete={() => {
            navigate("/", { replace: true });
          }}
          onResolve={(resolveId, resolved, message) => {
            setPost(prev => prev && prev.id === resolveId ? {
              ...prev,
              status: resolved ? "RESOLVED" : "ACTIVE",
              isResolved: resolved,
              resolutionMessage: resolved ? message : undefined,
              reopened: !resolved,
              isReopened: !resolved,
              reopenedReason: !resolved ? message : undefined,
            } as any : prev);
          }}
        />
      </div>
    </div>
  );
};

export default PostDetail;