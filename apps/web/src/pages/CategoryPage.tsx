import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Helmet } from 'react-helmet-async';
import { Tag, ArrowLeft, Loader2 } from 'lucide-react';
import axiosInstance from '../api/axiosConfig';
import PostCard from '../components/post/PostCard';
import { toPostCardPost } from '../utils/postUtils';

const CategoryPage: React.FC = () => {
  const { categorySlug } = useParams<{ categorySlug: string }>();
  const navigate = useNavigate();
  const [posts, setPosts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const categoryName = categorySlug
    ? categorySlug
        .split('-')
        .map(
          (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase(),
        )
        .join(' ')
    : 'Category';

  useEffect(() => {
    const fetchCategoryPosts = async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await axiosInstance.get('/api/search', {
          params: { q: categoryName, limit: 10, type: 'POST' },
        });
        const container = res.data?.data ?? res.data;
        const items = Array.isArray(container)
          ? container
          : (container?.content ?? container?.data ?? []);
        setPosts(items.map((item: any) => toPostCardPost(item.post || item)));
      } catch (err: any) {
        setError(
          err.response?.data?.message ||
            err.message ||
            'Failed to load category feed.',
        );
      } finally {
        setLoading(false);
      }
    };

    fetchCategoryPosts();
  }, [categoryName]);

  const pageTitle = `${categoryName} Civic Issues & Reports | Govlyx`;
  const pageDesc = `Browse public complaints, updates, and civic solutions related to ${categoryName} on Govlyx.`;
  const canonicalUrl = `https://govlyx.com/category/${categorySlug || ''}`;

  const breadcrumbJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      {
        '@type': 'ListItem',
        position: 1,
        name: 'Home',
        item: 'https://govlyx.com',
      },
      {
        '@type': 'ListItem',
        position: 2,
        name: `${categoryName} Category`,
        item: canonicalUrl,
      },
    ],
  };

  return (
    <div className="max-w-4xl mx-auto p-4 sm:p-6 space-y-6">
      <Helmet>
        <title>{pageTitle}</title>
        <meta name="description" content={pageDesc} />
        <link rel="canonical" href={canonicalUrl} />
        <meta property="og:title" content={pageTitle} />
        <meta property="og:description" content={pageDesc} />
        <meta property="og:url" content={canonicalUrl} />
        <meta property="og:site_name" content="Govlyx" />
        <meta property="og:type" content="website" />
        <meta property="og:image" content="https://govlyx.com/govlyx-og.png" />
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content={pageTitle} />
        <meta name="twitter:description" content={pageDesc} />
        <meta name="twitter:image" content="https://govlyx.com/govlyx-og.png" />
        <script type="application/ld+json">
          {JSON.stringify(breadcrumbJsonLd)}
        </script>
      </Helmet>

      <button
        onClick={() => navigate(-1)}
        className="btn btn-ghost btn-sm gap-2 opacity-50 hover:opacity-100 -ml-2"
      >
        <ArrowLeft size={18} />
        Back
      </button>

      <div className="flex items-center gap-3 border-b border-base-300 pb-4">
        <div className="w-12 h-12 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-600">
          <Tag size={24} />
        </div>
        <div>
          <h1 className="text-2xl font-bold">{categoryName}</h1>
          <p className="text-sm opacity-60">
            Civic issues filed under the {categoryName} category
          </p>
        </div>
      </div>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-12 gap-4">
          <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
          <p className="text-sm opacity-50 font-medium">
            Loading category feed...
          </p>
        </div>
      ) : error ? (
        <div className="text-center py-12 opacity-50">
          <p className="text-sm text-error">{error}</p>
        </div>
      ) : posts.length === 0 ? (
        <div className="text-center py-12 opacity-50">
          <p className="text-sm">No issues found under {categoryName} yet.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {posts.map((post) => (
            <PostCard key={post.id} post={post} readOnly />
          ))}
        </div>
      )}
    </div>
  );
};

export default CategoryPage;
