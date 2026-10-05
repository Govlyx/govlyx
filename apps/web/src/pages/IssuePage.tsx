import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { ArrowLeft, Loader2, AlertCircle } from 'lucide-react';
import { postService } from '../api/postService';
import PostCard from '../components/post/PostCard';
import { toPostCardPost } from '../utils/postUtils';
import { getAuthToken } from '../utils/auth';
import { useCurrentUser } from '../hooks/useUser';

const IssuePage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [post, setPost] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { data: user } = useCurrentUser({ enabled: !!getAuthToken() });

  const currentUser = user
    ? {
        id: user.id,
        username: user.actualUsername || user.username,
        role: user.role,
      }
    : undefined;

  useEffect(() => {
    const fetchPost = async () => {
      if (!id) return;
      setLoading(true);
      setError(null);
      try {
        const raw = await postService.getPostById(Number(id), 'posts');
        const actualPost = raw?.data || raw;
        if (!actualPost) {
          throw new Error('Post not found.');
        }
        setPost(toPostCardPost(actualPost));
      } catch (err: any) {
        setError(
          err.response?.data?.message || err.message || 'Failed to load issue.',
        );
      } finally {
        setLoading(false);
      }
    };

    fetchPost();
  }, [id]);

  const snippet = post?.content
    ? post.content.length > 150
      ? post.content.substring(0, 150) + '...'
      : post.content
    : 'Read details on this civic issue.';
  const title = post?.content
    ? (post.content.length > 50
        ? post.content.substring(0, 50) + '...'
        : post.content) + ' | Govlyx'
    : 'Civic Issue | Govlyx';
  const ogImage =
    post?.mediaUrls?.[0] ||
    post?.mediaUrl ||
    post?.imageUrl ||
    'https://govlyx.com/govlyx-og.png';
  const canonicalUrl = `https://govlyx.com/issue/${id}`;

  const jsonLd = post
    ? {
        '@context': 'https://schema.org',
        '@type': 'SocialMediaPosting',
        headline: post.content
          ? post.content.substring(0, 100)
          : 'Civic Issue on Govlyx',
        articleBody: post.content || snippet,
        datePublished: post.createdAt,
        image: ogImage,
        author: {
          '@type': 'Person',
          name: post.authorUsername || post.authorName || 'Citizen',
        },
        publisher: {
          '@type': 'Organization',
          name: 'Govlyx',
          url: 'https://govlyx.com',
        },
      }
    : null;

  return (
    <div className="max-w-4xl mx-auto p-4 sm:p-6 space-y-6">
      <Helmet>
        <title>{title}</title>
        <meta name="description" content={snippet} />
        <link rel="canonical" href={canonicalUrl} />
        <meta property="og:title" content={title} />
        <meta property="og:description" content={snippet} />
        <meta property="og:url" content={canonicalUrl} />
        <meta property="og:site_name" content="Govlyx" />
        <meta property="og:type" content="article" />
        <meta property="og:image" content={ogImage} />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content={title} />
        <meta name="twitter:description" content={snippet} />
        <meta name="twitter:image" content={ogImage} />
        {jsonLd && (
          <script type="application/ld+json">{JSON.stringify(jsonLd)}</script>
        )}
      </Helmet>

      <button
        onClick={() => navigate(-1)}
        className="btn btn-ghost btn-sm gap-2 opacity-50 hover:opacity-100 -ml-2"
      >
        <ArrowLeft size={18} />
        Back
      </button>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-12 gap-4">
          <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
          <p className="text-sm opacity-50 font-medium">
            Loading issue details...
          </p>
        </div>
      ) : error || !post ? (
        <div className="bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-900 rounded-2xl p-8 text-center flex flex-col items-center gap-4">
          <div className="w-16 h-16 bg-red-100 dark:bg-red-900/50 rounded-full flex items-center justify-center text-red-600 dark:text-red-400">
            <AlertCircle size={32} />
          </div>
          <h2 className="text-xl font-bold text-red-900 dark:text-red-200">
            Oops! Content Unavailable
          </h2>
          <p className="text-red-700 dark:text-red-400 max-w-sm">
            {error || "We couldn't find the issue you're looking for."}
          </p>
        </div>
      ) : (
        <div className="w-full">
          <PostCard
            post={post}
            currentUser={currentUser}
            readOnly={!currentUser}
          />
        </div>
      )}
    </div>
  );
};

export default IssuePage;
