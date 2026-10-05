import { useState, useEffect } from 'react';
import { sidebarService } from '../../api/sidebarService';
import type { SidebarResponseDto } from '../../api/sidebarService';
import AreaPulseWidget from './AreaPulseWidget';
import UnansweredQuestionsWidget from './UnansweredQuestionsWidget';
import TopUnresolvedIssueWidget from './TopUnresolvedIssueWidget';
import OfficialAlertWidget from './OfficialAlertWidget';

const SkeletonCard = () => (
  <div className="p-3 rounded-2xl border border-base-300 bg-base-100/30 animate-pulse flex flex-col gap-2">
    <div className="h-3 w-2/5 bg-base-300 rounded animate-pulse" />
    <div className="h-px bg-base-200 w-full my-0.5 animate-pulse" />
    <div className="space-y-1.5">
      <div className="h-3.5 bg-base-300 rounded w-full animate-pulse" />
      <div className="h-3.5 bg-base-300 rounded w-5/6 animate-pulse" />
    </div>
  </div>
);

const SidebarRight = () => {
  const [activeTab, setActiveTab] = useState<string>(() => {
    return sessionStorage.getItem('active_home_tab') || 'all';
  });
  const [data, setData] = useState<SidebarResponseDto | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const handleTabChange = (e: Event) => {
      const customEvent = e as CustomEvent;
      if (customEvent.detail?.tab) {
        setActiveTab(customEvent.detail.tab);
      }
    };

    window.addEventListener('homeTabChanged', handleTabChange);
    return () => {
      window.removeEventListener('homeTabChanged', handleTabChange);
    };
  }, []);

  useEffect(() => {
    let active = true;
    const fetchData = async () => {
      setLoading(true);
      try {
        const sidebarData = await sidebarService.getSidebarData(activeTab);
        if (active) {
          setData(sidebarData);
        }
      } catch (err) {
        console.error('Failed to load sidebar data:', err);
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    fetchData();
    return () => {
      active = false;
    };
  }, [activeTab]);

  const renderConditionalWidgets = () => {
    if (loading) {
      return (
        <>
          <SkeletonCard />
          <SkeletonCard />
        </>
      );
    }

    switch (activeTab) {
      case 'all':
        return (
          <>
            {data?.topUnresolvedIssue && (
              <TopUnresolvedIssueWidget post={data.topUnresolvedIssue} />
            )}
            {data?.unansweredQuestions &&
              data.unansweredQuestions.length > 0 && (
                <UnansweredQuestionsWidget posts={data.unansweredQuestions} />
              )}
          </>
        );
      case 'location':
        return (
          <>
            {data?.latestOfficialAlert && (
              <OfficialAlertWidget post={data.latestOfficialAlert} />
            )}
            {data?.unansweredQuestions &&
              data.unansweredQuestions.length > 0 && (
                <UnansweredQuestionsWidget posts={data.unansweredQuestions} />
              )}
          </>
        );
      case 'official':
        return (
          <>
            {data?.topUnresolvedIssue && (
              <TopUnresolvedIssueWidget post={data.topUnresolvedIssue} />
            )}
          </>
        );
      case 'following':
        return (
          <>
            {data?.unansweredQuestions &&
              data.unansweredQuestions.length > 0 && (
                <UnansweredQuestionsWidget posts={data.unansweredQuestions} />
              )}
            {data?.latestOfficialAlert && (
              <OfficialAlertWidget post={data.latestOfficialAlert} />
            )}
          </>
        );
      case 'neighborhood_qa':
        return (
          <>
            {data?.topUnresolvedIssue && (
              <TopUnresolvedIssueWidget post={data.topUnresolvedIssue} />
            )}
            {data?.unansweredQuestions &&
              data.unansweredQuestions.length > 0 && (
                <UnansweredQuestionsWidget posts={data.unansweredQuestions} />
              )}
            {data?.latestOfficialAlert && (
              <OfficialAlertWidget post={data.latestOfficialAlert} />
            )}
          </>
        );
      default:
        return (
          <>
            {data?.topUnresolvedIssue && (
              <TopUnresolvedIssueWidget post={data.topUnresolvedIssue} />
            )}
            {data?.unansweredQuestions &&
              data.unansweredQuestions.length > 0 && (
                <UnansweredQuestionsWidget posts={data.unansweredQuestions} />
              )}
          </>
        );
    }
  };

  return (
    <aside className="flex min-h-full flex-col gap-2 pb-2">
      {/* Area Pulse - Always visible if loaded */}
      {loading ? (
        <SkeletonCard />
      ) : (
        data?.areaPulse && <AreaPulseWidget data={data.areaPulse} />
      )}

      {/* Conditionally rendered widgets based on current active tab */}
      {renderConditionalWidgets()}
    </aside>
  );
};

export default SidebarRight;
