import axiosInstance from "./axiosConfig";

export interface AreaPulseDto {
  totalIssuesThisWeek: number;
  resolvedIssuesThisWeek: number;
  unansweredQuestions: number;
}

export type SocialPostDto = any;
export type PostResponse = any;

export interface SidebarResponseDto {
  activeTab: string;
  areaPulse?: AreaPulseDto;
  unansweredQuestions?: SocialPostDto[];
  topUnresolvedIssue?: PostResponse;
  latestOfficialAlert?: PostResponse;
}

const MOCK_PULSE: AreaPulseDto = {
  totalIssuesThisWeek: 18,
  resolvedIssuesThisWeek: 12,
  unansweredQuestions: 6,
};

const MOCK_TOP_ISSUE = {
  id: 901,
  variant: "issue",
  content: "Massive pothole on sector 4 main road blocking traffic lanes and causing bike accidents. Needs urgent municipal intervention.",
  category: "ROADS & TRANSPORT",
  createdAt: new Date(Date.now() - 3600000 * 24 * 3).toISOString(),
  likeCount: 57,
  commentCount: 14,
  status: "ACTIVE",
  isDepartmentTagged: true,
};

const MOCK_OFFICIAL_ALERT = {
  id: 902,
  variant: "government",
  content: "Scheduled electricity maintenance shut down in sectors 3, 4, and 5 this Friday from 9:00 AM to 1:00 PM.",
  department: "Electricity Distribution Board",
  createdAt: new Date(Date.now() - 3600000 * 4).toISOString(),
  commentCount: 8,
  isGovernmentBroadcast: true,
};

const MOCK_QUESTIONS = [
  {
    id: 903,
    variant: "social",
    content: "Does anyone know if the public library in sector 8 is open on Sundays?",
    username: "book_worm99",
    createdAt: new Date(Date.now() - 3600000 * 2).toISOString(),
    commentCount: 0,
  },
  {
    id: 904,
    variant: "social",
    content: "Who is the recommended local plumber for fixing gas geysers?",
    username: "geyser_trouble",
    createdAt: new Date(Date.now() - 3600000 * 5).toISOString(),
    commentCount: 0,
  },
  {
    id: 905,
    variant: "social",
    content: "Any reliable veterinarian clinic open 24/7 in our area?",
    username: "paw_parent21",
    createdAt: new Date(Date.now() - 3600000 * 12).toISOString(),
    commentCount: 0,
  },
  {
    id: 906,
    variant: "social",
    content: "Where can I get original vehicle pollution certificate done quickly?",
    username: "daily_commuter",
    createdAt: new Date(Date.now() - 3600000 * 18).toISOString(),
    commentCount: 0,
  },
  {
    id: 907,
    variant: "social",
    content: "Is morning jogging permitted in the central botanical garden currently?",
    username: "fitness_enthusiast",
    createdAt: new Date(Date.now() - 3600000 * 24).toISOString(),
    commentCount: 0,
  },
];

const MOCK_SIDEBAR_DATA: Record<string, SidebarResponseDto> = {
  all: {
    activeTab: "all",
    areaPulse: MOCK_PULSE,
    topUnresolvedIssue: MOCK_TOP_ISSUE,
    unansweredQuestions: MOCK_QUESTIONS,
  },
  location: {
    activeTab: "location",
    areaPulse: MOCK_PULSE,
    latestOfficialAlert: MOCK_OFFICIAL_ALERT,
    unansweredQuestions: MOCK_QUESTIONS,
  },
  official: {
    activeTab: "official",
    areaPulse: MOCK_PULSE,
    topUnresolvedIssue: MOCK_TOP_ISSUE,
  },
  following: {
    activeTab: "following",
    areaPulse: MOCK_PULSE,
    unansweredQuestions: MOCK_QUESTIONS,
    latestOfficialAlert: MOCK_OFFICIAL_ALERT,
  },
  neighborhood_qa: {
    activeTab: "neighborhood_qa",
    areaPulse: MOCK_PULSE,
    topUnresolvedIssue: MOCK_TOP_ISSUE,
    latestOfficialAlert: MOCK_OFFICIAL_ALERT,
  },
};

export const sidebarService = {
  getSidebarData: async (tabName: string): Promise<SidebarResponseDto> => {
    let result: SidebarResponseDto | null = null;
    try {
      const response = await axiosInstance.get(`/api/v1/sidebar`, {
        params: { tab: tabName }
      });
      const json = response.data;
      const isWrapped = json.success !== undefined && json.data !== undefined;
      result = isWrapped ? json.data : json;
    } catch {
      result = null;
    }

    // If result is null or unansweredQuestions is empty or areaPulse is 0,
    // query live feed endpoints (/api/v1/feed/neighborhood-qa, /api/v1/feed/local, /api/v1/feed/for-you)
    if (!result || !result.unansweredQuestions || result.unansweredQuestions.length === 0) {
      try {
        const [qaRes, localRes, forYouRes] = await Promise.allSettled([
          axiosInstance.get("/api/v1/feed/neighborhood-qa?size=10&sort=NEW"),
          axiosInstance.get("/api/v1/feed/local?size=10&sort=NEW"),
          axiosInstance.get("/api/v1/feed/for-you?size=10&sort=NEW"),
        ]);

        let liveQAs: any[] = [];
        if (qaRes.status === "fulfilled" && qaRes.value.status === 200) {
          const raw = qaRes.value.data;
          const container = raw.success !== undefined && raw.data !== undefined ? raw.data : raw;
          liveQAs = Array.isArray(container) ? container : (container?.data ?? container?.content ?? []);
        }

        let liveLocal: any[] = [];
        if (localRes.status === "fulfilled" && localRes.value.status === 200) {
          const raw = localRes.value.data;
          const container = raw.success !== undefined && raw.data !== undefined ? raw.data : raw;
          liveLocal = Array.isArray(container) ? container : (container?.data ?? container?.content ?? []);
        }

        let liveForYou: any[] = [];
        if (forYouRes.status === "fulfilled" && forYouRes.value.status === 200) {
          const raw = forYouRes.value.data;
          const container = raw.success !== undefined && raw.data !== undefined ? raw.data : raw;
          liveForYou = Array.isArray(container) ? container : (container?.data ?? container?.content ?? []);
        }

        const allCombined = [...liveQAs, ...liveLocal, ...liveForYou];
        const seen = new Set();
        const unanswered: any[] = [];

        for (const p of allCombined) {
          if (!p || !p.id || seen.has(p.id)) continue;
          seen.add(p.id);
          unanswered.push({
            id: p.id,
            variant: p.variant || "social",
            content: p.content || p.title || "",
            username: p.authorUsername || p.username || (p.author ? p.author.username : "Neighbor"),
            createdAt: p.createdAt || new Date().toISOString(),
            commentCount: p.commentCount ?? 0,
          });
        }

        const issuePosts = allCombined.filter((p: any) => p && (p.variant === "issue" || p.isDepartmentTagged === true));
        const topIssue = allCombined.find((p: any) => 
          p && (p.status === "ACTIVE" || p.isResolved === false) && (p.variant === "issue" || p.isDepartmentTagged === true)
        ) || issuePosts[0];

        const questionsToUse = unanswered.length > 0 ? unanswered.slice(0, 5) : MOCK_QUESTIONS;

        if (!result) {
          result = {
            activeTab: tabName,
            areaPulse: {
              totalIssuesThisWeek: issuePosts.length > 0 ? issuePosts.length : (MOCK_PULSE.totalIssuesThisWeek),
              resolvedIssuesThisWeek: issuePosts.filter((p: any) => p.status === "RESOLVED" || p.isResolved).length,
              unansweredQuestions: questionsToUse.length,
            },
            topUnresolvedIssue: topIssue ? {
              id: topIssue.id,
              variant: topIssue.variant || "issue",
              content: topIssue.content || topIssue.title || "",
              category: topIssue.category || topIssue.departmentName || "LOCAL ISSUE",
              createdAt: topIssue.createdAt || new Date().toISOString(),
              likeCount: topIssue.likeCount ?? topIssue.upvotes ?? 0,
              commentCount: topIssue.commentCount ?? 0,
              status: topIssue.status || "ACTIVE",
            } : (MOCK_SIDEBAR_DATA[tabName]?.topUnresolvedIssue || MOCK_TOP_ISSUE),
            unansweredQuestions: questionsToUse,
          };
        } else {
          if (!result.unansweredQuestions || result.unansweredQuestions.length === 0) {
            result.unansweredQuestions = questionsToUse;
          }
          if (result.areaPulse && result.areaPulse.unansweredQuestions === 0 && questionsToUse.length > 0) {
            result.areaPulse.unansweredQuestions = questionsToUse.length;
          }
        }
      } catch {
        if (!result) {
          result = MOCK_SIDEBAR_DATA[tabName] || MOCK_SIDEBAR_DATA.all;
        }
      }
    }

    return result || MOCK_SIDEBAR_DATA[tabName] || MOCK_SIDEBAR_DATA.all;
  }
};
