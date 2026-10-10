import { useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import {
  ShieldCheck,
  TrendingUp,
  Search,
  RefreshCw,
  Eye,
  EyeOff,
  UserPlus,
  Globe,
  Landmark,
  Map,
  MapPin,
  Heart,
  MessageSquare,
  ShieldAlert,
  User,
  Shield,
  Building,
  ExternalLink,
  Loader2
} from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { departmentRegisterSchema, adminRegisterSchema } from "../utils/validation";
import { showToast } from "../utils/toast";
import axiosInstance from "../api/axiosConfig";
import { decodeHTML } from "../utils/postUtils";


import DOMPurify from "dompurify";

const inferDepartmentType = (name: string) => {
  const n = (name || "").toLowerCase();
  if (n.includes("water")) return "Water Supply";
  if (n.includes("road") || n.includes("infra")) return "Roads & Infrastructure";
  if (n.includes("sanit") || n.includes("waste")) return "Sanitation & Waste";
  if (n.includes("elec") || n.includes("power")) return "Electricity & Power";
  if (n.includes("health") || n.includes("medical")) return "Health & Medical";
  if (n.includes("police") || n.includes("safety")) return "Public Safety";
  return "Public Services";
};

const formatRegDate = (dateStr: string) => {
  if (!dateStr) return "N/A";
  try {
    const d = new Date(dateStr);
    const now = new Date();
    
    // If it's today
    if (d.toDateString() === now.toDateString()) {
      return `Today, ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })}`;
    }
    
    // If it's yesterday
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    if (d.toDateString() === yesterday.toDateString()) {
      return `Yesterday, ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })}`;
    }
    
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch (e) {
    return dateStr;
  }
};

const formatJoinedDate = (dateStr: string) => {
  if (!dateStr) return "N/A";
  try {
    const d = new Date(dateStr);
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch (e) {
    return dateStr;
  }
};

const DepartmentBroadcastCount = ({ userId }: { userId: number }) => {
  const [count, setCount] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    const getCount = async () => {
      try {
        const postsRes = await axiosInstance.get(`/api/posts/user/${userId}`, { params: { limit: 150 } }).catch(() => null);
        let rawPosts = postsRes ? (postsRes.data?.data?.data ?? postsRes.data?.data?.content ?? postsRes.data?.data ?? postsRes.data?.content ?? []) : [];

        if (!postsRes || rawPosts.length === 0) {
          const fallbackRes = await axiosInstance.get(`/api/posts`, { params: { limit: 150 } }).catch(() => null);
          const allPosts = fallbackRes ? (fallbackRes.data?.data?.data ?? fallbackRes.data?.data?.content ?? fallbackRes.data?.data ?? fallbackRes.data?.content ?? []) : [];
          rawPosts = allPosts.filter((p: any) => p.userId === userId);
        }

        if (active) {
          setCount(rawPosts.length);
        }
      } catch (err) {
        if (active) setCount(0);
      }
    };
    getCount();
    return () => { active = false; };
  }, [userId]);

  if (count === null) {
    return <span className="opacity-50 text-xs">...</span>;
  }
  return <span>{count}</span>;
};

const AdminDashboard = () => {
  // Navigation State — tab is driven by URL ?tab= param so the left sidebar can control it
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get("tab") || "dashboard";
  const setActiveTab = (tab: string) => setSearchParams({ tab }, { replace: true });

  // Stats State
  const [liveSessions, setLiveSessions] = useState<number | null>(null);
  const [overviewStats, setOverviewStats] = useState<any>(null);
  const [broadcastStats, setBroadcastStats] = useState<any>(null);
  const [loadingStats, setLoadingStats] = useState<boolean>(false);
  const [onlineCount, setOnlineCount] = useState<number | null>(null);
  const [recentActivities, setRecentActivities] = useState<any[]>([]);
  const [communityStats, setCommunityStats] = useState<any>(null);

  // Users Filter State
  const [usersRoleFilter, setUsersRoleFilter] = useState<string>("all");

  // Search State
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedUserForDetails, setSelectedUserForDetails] = useState<any | null>(null);
  const [panelPosts, setPanelPosts] = useState<any[]>([]);
  const [panelLoading, setPanelLoading] = useState(false);
  const [userContributionsCount, setUserContributionsCount] = useState<number | null>(null);
  const [panelFilter, setPanelFilter] = useState("all");

  // Broadcasts Tab State
  const [broadcastsList, setBroadcastsList] = useState<any[]>([]);
  const [loadingBroadcasts, setLoadingBroadcasts] = useState<boolean>(false);
  const [broadcastSearch, setBroadcastSearch] = useState<string>("");

  // System Health Tab State
  const [healthData, setHealthData] = useState<any>(null);
  const [loadingHealth, setLoadingHealth] = useState<boolean>(false);
  const [apiLatency, setApiLatency] = useState<number | null>(null);
  const [executingAction, setExecutingAction] = useState<string | null>(null);

  // Copyright Claims Tab State
  const [claimsList, setClaimsList] = useState<any[]>([]);
  const [loadingClaims, setLoadingClaims] = useState<boolean>(false);
  const [claimsPage, setClaimsPage] = useState<number>(1);
  const [claimsTotalPages, setClaimsTotalPages] = useState<number>(1);
  const [selectedClaimForReview, setSelectedClaimForReview] = useState<any | null>(null);
  const [acknowledgingClaim, setAcknowledgingClaim] = useState<boolean>(false);



  // Onboarding Requests State (localStorage based)
  const [requests, setRequests] = useState<any[]>([]);
  const [showApproveModal, setShowApproveModal] = useState<any | null>(null);
  const [approvedForm, setApprovedForm] = useState({
    email: "",
    password: "",
    identity: ""
  });

  // Password Visibility toggles
  const [showDeptPw, setShowDeptPw] = useState(false);
  const [showDeptConf, setShowDeptConf] = useState(false);
  const [showAdminPw, setShowAdminPw] = useState(false);
  const [showAdminConf, setShowAdminConf] = useState(false);

  // Department Registration Form State
  const [deptForm, setDeptForm] = useState({
    name: "",
    email: "",
    password: "",
    confirmPassword: "",
    pincode: ""
  });
  const [registeringDept, setRegisteringDept] = useState(false);

  // Admin Registration Form State
  const [adminForm, setAdminForm] = useState({
    username: "",
    email: "",
    pincode: "",
    password: "",
    confirmPassword: ""
  });
  const [registeringAdmin, setRegisteringAdmin] = useState(false);

  // Users Database State
  const [usersDb, setUsersDb] = useState<any[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);

  // Communities Database State (Mock + LocalStorage)
  const [communitiesDb, setCommunitiesDb] = useState<any[]>([]);
  const [fetchingCommunities, setFetchingCommunities] = useState(false);

  // Departments Database State
  const [deptsDb, setDeptsDb] = useState<any[]>([]);
  const [loadingDepts, setLoadingDepts] = useState(false);

  // Content moderation states
  const [moderationStats, setModerationStats] = useState({
    totalPending: 0,
    emergencyPending: 0,
    standardPending: 0,
    totalResolved: 0
  });
  const [emergencyReports, setEmergencyReports] = useState<any[]>([]);
  const [standardReports, setStandardReports] = useState<any[]>([]);
  const [historyReports, setHistoryReports] = useState<any[]>([]);
  const [loadingModeration, setLoadingModeration] = useState(false);
  // moderationSubTab is currently not used in UI viewports
  // const [moderationSubTab, setModerationSubTab] = useState<"emergency" | "standard" | "history">("emergency");
  const [resolvingReportId, setResolvingReportId] = useState<number | null>(null);
  const [resolveForm, setResolveForm] = useState({
    resolution: "RESOLVED_REMOVED",
    notes: ""
  });
  const [submittingResolution, setSubmittingResolution] = useState(false);
  const [reportContents, setReportContents] = useState<Record<string, { content: string; author?: string; mediaUrls?: string[]; error?: boolean; loading: boolean }>>({});

  // Live search states for workable user view
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [searchingLive, setSearchingLive] = useState(false);

  // Fetch initial stats and list data
  useEffect(() => {
    fetchStats();
    fetchLiveDepartments();
    fetchLiveUsers();
    
    // Load onboarding requests from localStorage
    const savedReqs = JSON.parse(localStorage.getItem("dept_requests") || "[]");
    setRequests(savedReqs);

    // Real-time synchronization is handled directly by API calls.
  }, []);

  const fetchStats = async () => {
    setLoadingStats(true);
    try {


      // Fetch chat statistics (fail-safe)
      const chatRes = await axiosInstance.get("/api/chat/admin/statistics").catch(() => null);
      if (chatRes && chatRes.data) {
        const stats = chatRes.data?.data ?? chatRes.data;
        if (stats.activeSessions !== undefined) {
          setLiveSessions(stats.activeSessions);
        }
      }

      // Fetch dashboard overview stats
      const overviewRes = await axiosInstance.get("/api/admin/dashboard/overview").catch(() => null);
      if (overviewRes && overviewRes.data) {
        const data = overviewRes.data?.data ?? overviewRes.data;
        setOverviewStats(data);
        if (data.activeChatSessions !== undefined) {
          setLiveSessions(data.activeChatSessions);
        }
      }

      // Fetch online user count
      const onlineRes = await axiosInstance.get("/api/chat/online-count").catch(() => null);
      if (onlineRes && onlineRes.data) {
        const data = onlineRes.data?.data ?? onlineRes.data;
        if (data.onlineCount !== undefined) {
          setOnlineCount(Number(data.onlineCount));
        }
      }

      // Fetch recent activity
      const activityRes = await axiosInstance.get("/api/admin/activity/recent").catch(() => null);
      if (activityRes && activityRes.data) {
        const data = activityRes.data?.data ?? activityRes.data;
        setRecentActivities(Array.isArray(data) ? data : []);
      }

      // Fetch community stats
      const commStatsRes = await axiosInstance.get("/api/admin/communities/stats").catch(() => null);
      if (commStatsRes && commStatsRes.data) {
        const data = commStatsRes.data?.data ?? commStatsRes.data;
        setCommunityStats(data);
      }

      // Fetch broadcast statistics
      const broadcastStatsRes = await axiosInstance.get("/api/posts/broadcast/statistics").catch(() => null);
      if (broadcastStatsRes && broadcastStatsRes.data) {
        const data = broadcastStatsRes.data?.data ?? broadcastStatsRes.data;
        setBroadcastStats(data);
      }
    } catch (e) {
      console.warn("Failed fetching dashboard stats from backend, displaying simulations.");
    } finally {
      setLoadingStats(false);
    }
  };

  // Live search user effect
  useEffect(() => {
    if (!searchQuery.trim() || searchQuery.trim().length < 2) {
      setSearchResults([]);
      return;
    }
    const delayDebounce = setTimeout(async () => {
      setSearchingLive(true);
      try {
        const res = await axiosInstance.get(`/api/users/search?query=${encodeURIComponent(searchQuery)}&limit=20`);
        const data = res.data?.data?.data ?? res.data?.data?.content ?? res.data?.data ?? res.data?.content ?? [];
        if (Array.isArray(data)) {
          const mapped = data.map((u: any) => ({
            id: u.id,
            username: u.actualUsername || u.username,
            email: u.email || `${u.username}@govlyx.io`,
            pincode: u.primaryLocation || u.location || u.pincode || "N/A",
            role: u.role || "ROLE_USER",
            status: "Active",
            regDate: u.createdAt ? new Date(u.createdAt).toISOString().split("T")[0] : "2026-05-25"
          }));
          setSearchResults(mapped);
        }
      } catch (err) {
        console.warn("Failed fetching live users from search API:", err);
      } finally {
        setSearchingLive(false);
      }
    }, 300);

    return () => clearTimeout(delayDebounce);
  }, [searchQuery]);

  // Helper: extract paginated array from any API response shape
  const extractPaginatedData = (res: any): any[] => {
    if (!res?.data) return [];
    const body = res.data; // ApiResponse
    const paged = body?.data; // PaginatedResponse
    if (Array.isArray(paged)) return paged;
    if (Array.isArray(paged?.data)) return paged.data;
    if (Array.isArray(paged?.content)) return paged.content;
    if (Array.isArray(body?.content)) return body.content;
    if (Array.isArray(body)) return body;
    return [];
  };

  // Fetch all users with multi-tier API fallbacks
  const fetchLiveUsers = async () => {
    setLoadingUsers(true);
    try {
      // Fetch users by role in parallel — role is @JsonIgnore so we label them ourselves
      const [adminsRes, deptsRes, citizensRes] = await Promise.all([
        axiosInstance.get("/api/users/by-role/ROLE_ADMIN", { params: { limit: 50 } }).catch((e) => { console.error("[fetchLiveUsers] ROLE_ADMIN failed:", e?.response?.status, e?.response?.data); return null; }),
        axiosInstance.get("/api/users/by-role/ROLE_DEPARTMENT", { params: { limit: 50 } }).catch((e) => { console.error("[fetchLiveUsers] ROLE_DEPARTMENT failed:", e?.response?.status, e?.response?.data); return null; }),
        axiosInstance.get("/api/users/by-role/ROLE_USER", { params: { limit: 50 } }).catch((e) => { console.error("[fetchLiveUsers] ROLE_USER failed:", e?.response?.status, e?.response?.data); return null; }),
      ]);

      const admins = extractPaginatedData(adminsRes);
      const depts  = extractPaginatedData(deptsRes);
      const citizens = extractPaginatedData(citizensRes);


      const mapUser = (u: any, role: string) => ({
        id: u.id,
        username: u.actualUsername || u.username,
        email: u.email,
        pincode: u.primaryLocation || u.location || u.pincode || "N/A",
        role,
        status: u.isActive !== false ? "Active" : "Suspended",
        regDate: u.createdAt ? new Date(u.createdAt).toISOString().split("T")[0] : "N/A"
      });

      const allUsers = [
        ...admins.map((u: any) => mapUser(u, "ROLE_ADMIN")),
        ...depts.map((u: any) => mapUser(u, "ROLE_DEPARTMENT")),
        ...citizens.map((u: any) => mapUser(u, "ROLE_USER")),
      ];

      if (allUsers.length > 0) {
        setUsersDb(allUsers);
        return;
      }

      // Fallback: /api/users/active
      console.warn("[fetchLiveUsers] by-role returned empty, trying /api/users/active");
      const activeRes = await axiosInstance.get("/api/users/active", { params: { limit: 50 } }).catch((e) => { console.error("[fetchLiveUsers] /active failed:", e?.response?.status); return null; });
      const activeData = extractPaginatedData(activeRes);
      if (activeData.length > 0) {
        setUsersDb(activeData.map((u: any) => mapUser(u, "ROLE_USER")));
      }
    } catch (err) {
      console.error("[fetchLiveUsers] unexpected error:", err);
    } finally {
      setLoadingUsers(false);
    }
  };

  // Re-fetch live users when All Users tab is selected (refresh on each visit)
  useEffect(() => {
    if (activeTab === "users") {
      fetchLiveUsers();
    }
  }, [activeTab]);


  const fetchLiveDepartments = async () => {
    setLoadingDepts(true);
    try {
      // Clean approach: Call /api/admin/departments directly.
      // (This will work perfectly once the backend lazy-loading crash is fixed)
      const res = await axiosInstance.get("/api/admin/departments", { params: { limit: 100 } });
      const list = extractPaginatedData(res);
      
      const mapped = list.map((u: any) => ({
        id: u.id,
        name: u.actualUsername || u.username,
        email: u.email || "",
        pincode: u.primaryLocation || u.location || u.pincode || "N/A",
        status: u.isActive !== false ? "Active" : "Suspended",
        regDate: u.createdAt ? new Date(u.createdAt).toISOString().split("T")[0] : "N/A"
      }));

      setDeptsDb(mapped);
    } catch (err) {
      console.error("[fetchLiveDepartments] unexpected error:", err);
    } finally {
      setLoadingDepts(false);
    }
  };

  // Re-fetch live departments when Departments tab is selected (refresh on each visit)
  useEffect(() => {
    if (activeTab === "departments") {
      fetchLiveDepartments();
    }
  }, [activeTab]);

  const fetchLiveCommunities = async () => {
    setFetchingCommunities(true);
    try {
      const res = await axiosInstance.get("/api/communities?size=100");
      const data = res.data?.data?.data ?? res.data?.data?.content ?? res.data?.data ?? res.data?.content ?? res.data ?? [];
      if (Array.isArray(data)) {
        const mapped = data.map((c: any) => ({
          id: c.id,
          name: c.name || c.communityName || "",
          slug: c.slug || c.communitySlug || String(c.id),
          description: c.description || c.communityDescription || "",
          category: c.category || c.communityCategory || "General",
          privacy: c.privacy || c.communityPrivacy || "PUBLIC",
          memberCount: c.memberCount ?? c.communityMemberCount ?? 0,
          postCount: c.postCount ?? 0,
          status: c.archived ? "Archived" : "Active",
          regDate: c.createdAt ? new Date(c.createdAt).toISOString().split("T")[0] : "2026-05-25",
          healthScore: c.healthScore,
          healthTier: c.healthTier,
          healthTierEmoji: c.healthTierEmoji,
          feedEligible: c.feedEligible,
          locationName: c.locationName,
          wardName: c.wardName
        }));
        setCommunitiesDb(mapped);
      }
    } catch (err) {
      console.warn("Failed fetching live communities from backend:", err);
    } finally {
      setFetchingCommunities(false);
    }
  };

  const toggleCommunityStatus = async (id: number) => {
    try {
      await axiosInstance.delete(`/api/communities/${id}/archive`);
      showToast.success("Community archived successfully");
      fetchLiveCommunities();
    } catch (err) {
      console.warn("API call to archive community failed.");
      showToast.error("Failed to archive community.");
    }
  };

  // Trigger live communities fetch when Communities tab is selected
  useEffect(() => {
    if (activeTab === "communities") {
      fetchLiveCommunities();
      axiosInstance.get("/api/admin/communities/stats")
        .then(res => {
          if (res.data) setCommunityStats(res.data.data ?? res.data);
        })
        .catch(() => null);
    }
  }, [activeTab]);

  // Fetch all broadcasts from the dedicated endpoint (active + resolved)
  // GET /api/posts/broadcast already returns every broadcast regardless of status.
  const fetchLiveBroadcasts = async () => {
    setLoadingBroadcasts(true);
    try {
      const PAGE_SIZE = 100;
      const MAX_PAGES = 10; // 10 pages per status is plenty (up to 1000 items each)

      // Helper to fetch paginated posts from a given endpoint prefix
      const fetchFromEndpoint = async (endpoint: string) => {
        let beforeId: number | null = null;
        let hasMore = true;
        let page = 0;
        let results: any[] = [];

        while (hasMore && page < MAX_PAGES) {
          const url = beforeId
            ? `${endpoint}?limit=${PAGE_SIZE}&beforeId=${beforeId}`
            : `${endpoint}?limit=${PAGE_SIZE}`;
          const res: any = await axiosInstance.get(url).catch((err) => {
            console.error(`[fetchLiveBroadcasts] request error for ${url}:`, err);
            return null;
          });
          if (!res) break;

          const pageData = res.data?.data?.data ?? res.data?.data?.content ?? res.data?.data ?? res.data?.content ?? [];
          const pageList: any[] = Array.isArray(pageData) ? pageData : [];
          if (pageList.length === 0) break;

          results.push(...pageList);

          beforeId = res.data?.data?.nextCursor ?? (pageList.length > 0 ? pageList[pageList.length - 1].id : null);
          page++;

          const serverHasMore = res.data?.data?.hasMore ?? res.data?.hasMore ?? false;
          if (!serverHasMore || pageList.length < PAGE_SIZE) hasMore = false;
        }
        return results;
      };

      // Fetch active and resolved in parallel
      const [activeRaw, resolvedRaw] = await Promise.all([
        fetchFromEndpoint("/api/posts/active"),
        fetchFromEndpoint("/api/posts/resolved")
      ]);

      const combinedRaw = [...activeRaw, ...resolvedRaw];

      // Filter to keep posts with a broadcast scope
      const broadcasts = combinedRaw.filter((p: any) => p.isGovernmentBroadcast || p.broadcastScope);

      // Deduplicate by id
      const seen = new Set<number>();
      const combined: any[] = [];
      for (const item of broadcasts) {
        if (item?.id && !seen.has(item.id)) {
          seen.add(item.id);
          combined.push(item);
        }
      }
      combined.sort((a: any, b: any) => b.id - a.id);

      const mapped = combined.map((b: any) => ({
        id: b.id,
        username: b.username || b.author?.username || b.author?.actualUsername || "System",
        scope: b.broadcastScope || "AREA",
        target: b.targetPincodes?.join(", ") || b.targetDistricts?.join(", ") || b.targetStates?.join(", ") || b.targetCountry || "N/A",
        posted: b.createdAt ? new Date(b.createdAt).toLocaleString() : "N/A",
        resolved: b.isResolved || b.status === "RESOLVED" || b.resolved ? "Yes" : "No",
        content: b.content,
        isGovernmentBroadcast: b.isGovernmentBroadcast
      }));

      setBroadcastsList(mapped);
    } catch (err) {
      console.warn("Failed fetching broadcasts:", err);
    } finally {
      setLoadingBroadcasts(false);
    }
  };

  useEffect(() => {
    if (activeTab === "broadcast") {
      fetchLiveBroadcasts();
      fetchStats();
    }
  }, [activeTab]);

  const fetchSystemHealth = async () => {
    setLoadingHealth(true);
    const startTime = performance.now();
    try {
      const res = await axiosInstance.get("/api/admin/system/health");
      const endTime = performance.now();
      setApiLatency(Math.round(endTime - startTime));
      const data = res.data?.data ?? res.data;
      setHealthData(data);
    } catch (err) {
      console.warn("Failed fetching system health:", err);
    } finally {
      setLoadingHealth(false);
    }
  };

  useEffect(() => {
    if (activeTab === "system") {
      fetchSystemHealth();
    }
  }, [activeTab]);

  const fetchPendingClaims = async () => {
    setLoadingClaims(true);
    try {
      // Backend expects 0-indexed page, frontend uses 1-indexed page
      const res = await axiosInstance.get("/api/copyright-claims/admin/pending", {
        params: { page: claimsPage - 1, limit: 10 }
      });
      const data = res.data?.data ?? res.data ?? {};
      setClaimsList(data.content ?? data.data ?? []);
      setClaimsTotalPages(data.totalPages ?? 1);
    } catch (err) {
      console.error("Failed to fetch pending copyright claims:", err);
      showToast.error("Failed to fetch pending copyright claims");
    } finally {
      setLoadingClaims(false);
    }
  };

  const handleAcknowledgeClaim = async (id: number) => {
    setAcknowledgingClaim(true);
    try {
      await axiosInstance.put(`/api/copyright-claims/admin/${id}/acknowledge`);
      showToast.success("Claim acknowledged successfully.");
      setSelectedClaimForReview(null);
      fetchPendingClaims();
    } catch (err: any) {
      console.error("Failed to acknowledge claim:", err);
      showToast.error(
        err.response?.data?.message || err.message || "Failed to acknowledge claim"
      );
    } finally {
      setAcknowledgingClaim(false);
    }
  };

  useEffect(() => {
    if (activeTab === "copyright") {
      fetchPendingClaims();
    }
  }, [activeTab, claimsPage]);

  // Content Moderation Data Fetchers
  const fetchReportedContent = async (targetType: string, targetId: number) => {
    const key = `${targetType}-${targetId}`;
    if (reportContents[key]) return; // already fetched or fetching

    setReportContents(prev => ({
      ...prev,
      [key]: { content: "Loading reported content...", loading: true }
    }));

    try {
      const isSocial = targetType === "SOCIAL_POST";
      const endpoint = isSocial ? `/api/social-posts/${targetId}` : `/api/posts/${targetId}`;
      const res = await axiosInstance.get(endpoint);
      const post = res.data?.data ?? res.data;
      
      setReportContents(prev => ({
        ...prev,
        [key]: {
          content: post.content || "(No text content)",
          author: post.author?.actualUsername || post.author?.username || post.username || "anonymous",
          mediaUrls: post.mediaUrls || (post.imageName ? [post.imageName] : []),
          loading: false
        }
      }));
    } catch (err: any) {
      console.warn(`Failed to fetch content for ${key}:`, err);
      setReportContents(prev => ({
        ...prev,
        [key]: {
          content: err.response?.status === 404 ? "Content has already been removed or is unavailable (404)." : "Error loading content details.",
          author: "N/A",
          error: true,
          loading: false
        }
      }));
    }
  };

  const fetchModerationData = async () => {
    setLoadingModeration(true);
    try {
      // 1. Fetch Stats
      const statsRes = await axiosInstance.get("/api/reports/admin/stats");
      const statsData = statsRes.data?.data ?? statsRes.data;
      if (statsData) {
        setModerationStats({
          totalPending: statsData.totalPending ?? 0,
          emergencyPending: statsData.emergencyPending ?? 0,
          standardPending: statsData.standardPending ?? 0,
          totalResolved: statsData.totalResolved ?? 0
        });
      }

      // 2. Fetch Pending Emergency Reports
      const emergencyRes = await axiosInstance.get("/api/reports/admin/emergency?size=100");
      const emergencyData = emergencyRes.data?.data?.data ?? emergencyRes.data?.data?.content ?? emergencyRes.data?.data ?? emergencyRes.data?.content ?? [];
      setEmergencyReports(Array.isArray(emergencyData) ? emergencyData : []);

      // 3. Fetch Pending Standard Reports
      const standardRes = await axiosInstance.get("/api/reports/admin/standard?size=100");
      const standardData = standardRes.data?.data?.data ?? standardRes.data?.data?.content ?? standardRes.data?.data ?? standardRes.data?.content ?? [];
      setStandardReports(Array.isArray(standardData) ? standardData : []);

      // 4. Fetch All/History Reports
      const allRes = await axiosInstance.get("/api/reports/admin/all?size=100");
      const allData = allRes.data?.data?.data ?? allRes.data?.data?.content ?? allRes.data?.data ?? allRes.data?.content ?? [];
      setHistoryReports(Array.isArray(allData) ? allData : []);

    } catch (err: any) {
      console.error("Failed to fetch moderation data:", err);
      showToast.error("Failed to sync reports queue from server.");
    } finally {
      setLoadingModeration(false);
    }
  };

  useEffect(() => {
    if (activeTab === "content") {
      fetchModerationData();
    }
  }, [activeTab]);

  useEffect(() => {
    const reports = [...emergencyReports, ...standardReports, ...historyReports];
    reports.forEach(r => {
      if (r.targetId && r.targetType) {
        fetchReportedContent(r.targetType, r.targetId);
      }
    });
  }, [emergencyReports, standardReports, historyReports]);



  // Onboarding Request Actions (Commented out as the onboarding queue list is unused)
  /*
  const handleApproveRequest = (req: any) => {
    setShowApproveModal(req);
    setApprovedForm({
      email: `${req.deptName.toLowerCase().replace(/\s+/g, ".")}@govlyx.gov`,
      password: Math.random().toString(36).slice(-10),
      identity: `GOV-${Math.floor(1000 + Math.random() * 9000)}`
    });
  };
  */

  const confirmApproveRequest = async () => {
    if (!approvedForm.email || !approvedForm.password) {
      showToast.error("Email and password are required");
      return;
    }

    try {
      // Validate schema
      const parseResult = departmentRegisterSchema.safeParse({
        name: showApproveModal.deptName,
        email: approvedForm.email,
        password: approvedForm.password,
      });

      if (!parseResult.success) {
        const firstError = parseResult.error.issues[0]?.message || "Validation failed";
        showToast.error(firstError);
        return;
      }

      // Try hitting the backend API
      const registerPayload = {
        email: approvedForm.email,
        password: approvedForm.password,
        pincode: showApproveModal.pincode || "400001",
        username: showApproveModal.deptName
      };

      await axiosInstance.post("/api/auth/register/department", registerPayload);

      // Update request list
      const updatedReqs = requests.map(r => r.id === showApproveModal.id ? { ...r, status: "approved" } : r);
      setRequests(updatedReqs);
      localStorage.setItem("dept_requests", JSON.stringify(updatedReqs));

      // Fetch live departments list to update
      fetchLiveDepartments();

      showToast.success(`Approved! Credentials created for: ${approvedForm.email}`);
      setShowApproveModal(null);
    } catch (e: any) {
      const errMsg = e.response?.data?.message || e.message || "Failed to approve department onboarding.";
      showToast.error(errMsg);
    }
  };

  /*
  const handleRejectRequest = (id: number) => {
    const updated = requests.map(r => r.id === id ? { ...r, status: "rejected" } : r);
    setRequests(updated);
    localStorage.setItem("dept_requests", JSON.stringify(updated));
    showToast.info("Department onboarding request rejected.");
  };
  */

  // Register Department Account
  const handleRegisterDept = async (e: React.FormEvent) => {
    e.preventDefault();
    setRegisteringDept(true);

    if (deptForm.password !== deptForm.confirmPassword) {
      showToast.error("Passwords do not match");
      setRegisteringDept(false);
      return;
    }

    try {
      // Validate schema
      const parseResult = departmentRegisterSchema.safeParse({
        name: deptForm.name,
        email: deptForm.email,
        password: deptForm.password,
      });

      if (!parseResult.success) {
        const firstError = parseResult.error.issues[0]?.message || "Validation failed";
        showToast.error(firstError);
        setRegisteringDept(false);
        return;
      }

      const payload = {
        email: deptForm.email,
        password: deptForm.password,
        pincode: deptForm.pincode || "110001",
        username: deptForm.name
      };

      await axiosInstance.post("/api/auth/register/department", payload);

      // Fetch live list
      fetchLiveDepartments();

      showToast.success(`Department "${deptForm.name}" registered successfully!`);
      
      // Clear Form
      setDeptForm({
        name: "",
        email: "",
        password: "",
        confirmPassword: "",
        pincode: ""
      });
    } catch (e: any) {
      const errMsg = e.response?.data?.message || e.message || "Failed to register department.";
      showToast.error(errMsg);
    } finally {
      setRegisteringDept(false);
    }
  };

  const exportUsersToCSV = () => {
    try {
      const headers = ["ID", "Username", "Email", "Role", "Pincode", "Status", "Joined Date"];
      const rows = usersDb.map(u => [
        u.id,
        u.username,
        u.email || "",
        u.role,
        u.pincode || "",
        u.status,
        u.regDate || ""
      ]);

      const csvContent = [
        headers.join(","),
        ...rows.map(e => e.map(val => `"${String(val).replace(/"/g, '""')}"`).join(","))
      ].join("\n");

      const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.setAttribute("href", url);
      link.setAttribute("download", `govlyx_users_${new Date().toISOString().split("T")[0]}.csv`);
      link.style.visibility = "hidden";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      showToast.success("Users database exported to CSV successfully!");
    } catch (err) {
      showToast.error("Failed to export users to CSV");
    }
  };

  // Register Admin Account
  const handleRegisterAdmin = async (e: React.FormEvent) => {
    e.preventDefault();
    setRegisteringAdmin(true);

    if (adminForm.password !== adminForm.confirmPassword) {
      showToast.error("Passwords do not match");
      setRegisteringAdmin(false);
      return;
    }

    try {
      // Validate schema
      const parseResult = adminRegisterSchema.safeParse({
        username: adminForm.username,
        email: adminForm.email,
        password: adminForm.password,
      });

      if (!parseResult.success) {
        const firstError = parseResult.error.issues[0]?.message || "Validation failed";
        showToast.error(firstError);
        setRegisteringAdmin(false);
        return;
      }

      const payload = {
        email: adminForm.email,
        password: adminForm.password,
        pincode: adminForm.pincode || "110001",
        username: adminForm.username
      };

      await axiosInstance.post("/api/auth/register/admin", payload);

      // Fetch live list
      fetchLiveUsers();

      showToast.success(`Admin user "${adminForm.username}" registered successfully!`);

      // Clear Form
      setAdminForm({
        username: "",
        email: "",
        pincode: "",
        password: "",
        confirmPassword: ""
      });
    } catch (e: any) {
      const errMsg = e.response?.data?.message || e.message || "Failed to register admin.";
      showToast.error(errMsg);
    } finally {
      setRegisteringAdmin(false);
    }
  };

  // Open details slide-over pane
  const handleOpenDetails = async (user: any) => {
    setSelectedUserForDetails(user);
    setPanelLoading(true);
    setUserContributionsCount(null);
    setPanelPosts([]);

    if (!user.id) {
      setUserContributionsCount(0);
      setPanelPosts([]);
      setPanelLoading(false);
      return;
    }

    const isCitizen = user.role === "ROLE_USER";
    const countUrl = isCitizen ? `/api/social-posts/count/user/${user.id}` : `/api/posts/count/user/${user.id}`;
    const postsUrl = isCitizen ? `/api/social-posts/user/${user.id}` : `/api/posts/user/${user.id}`;

    try {
      // Fetch contributions count and posts list in parallel
      const [countRes, postsRes] = await Promise.all([
        axiosInstance.get(countUrl).catch(() => null),
        axiosInstance.get(postsUrl, { params: { limit: 150 } }).catch(() => null)
      ]);

      const count = countRes?.data?.data !== undefined ? Number(countRes.data.data) : 0;

      let rawPosts = extractPaginatedData(postsRes);

      // Fallback: If count > 0 but no posts returned for admin/dept, the deployed backend
      // might still be running the older build (returning 501 NOT IMPLEMENTED which is caught as null).
      // We fall back to loading the latest public posts list and filtering by userId.
      if (rawPosts.length === 0 && count > 0 && !isCitizen) {
        const fallbackRes = await axiosInstance.get(`/api/posts`, { params: { limit: 150 } }).catch(() => null);
        const allPosts = extractPaginatedData(fallbackRes);
        rawPosts = allPosts.filter((p: any) => p.userId === user.id);
      }

      const mapped = rawPosts.map((p: any) => ({
        id: p.id,
        type: isCitizen ? "social" : ((p.broadcastScope || p.isBroadcastPost) ? "broadcast" : (p.isResolved ? "resolved" : "social")),
        content: p.content,
        likes: p.likeCount || 0,
        comments: p.commentCount || 0,
        date: p.timeAgo || (p.createdAt ? new Date(p.createdAt).toLocaleDateString() : "N/A")
      }));
      setPanelPosts(mapped);
      setUserContributionsCount(mapped.length);
    } catch (err) {
      console.error("Failed to load user details from backend:", err);
      showToast.error("Failed to fetch activity log from server.");
      setUserContributionsCount(0);
      setPanelPosts([]);
    } finally {
      setPanelLoading(false);
    }
  };

  // User Management actions (Soft delete deactivation)
  const handleDeactivateUser = async (userId: number) => {
    if (!window.confirm("Are you sure you want to deactivate this user account?")) {
      return;
    }
    try {
      await axiosInstance.delete(`/api/admin/users/${userId}`);
      showToast.success("User account deactivated successfully");
      fetchLiveUsers();
      fetchLiveDepartments();
    } catch (err: any) {
      const errMsg = err.response?.data?.message || err.message || "Failed to deactivate user";
      showToast.error(errMsg);
    }
  };

  // Content moderation actions
  const handleOpenResolveDialog = (reportId: number) => {
    setResolvingReportId(reportId);
    setResolveForm({ resolution: "RESOLVED_REMOVED", notes: "" });
  };

  const submitResolveReport = async () => {
    if (!resolvingReportId) return;
    if (!resolveForm.notes.trim()) {
      showToast.error("Resolution notes are required for legal compliance record.");
      return;
    }

    setSubmittingResolution(true);
    try {
      await axiosInstance.put(`/api/reports/admin/${resolvingReportId}/resolve`, {
        resolution: resolveForm.resolution,
        notes: resolveForm.notes.trim()
      });

      showToast.success("Report resolved successfully.");
      setResolvingReportId(null);
      fetchModerationData();
    } catch (err: any) {
      console.error("Failed to resolve report:", err);
      const errMsg = err.response?.data?.message || err.response?.data?.error || err.message || "Failed to resolve report.";
      showToast.error(errMsg);
    } finally {
      setSubmittingResolution(false);
    }
  };

  // Bad words hot reload API integration
  const handleReloadBadWords = async () => {
    setExecutingAction("bad-words");
    try {
      const res = await axiosInstance.post("/api/admin/bad-words/reload");
      const data = res.data?.data ?? res.data;
      const wordsLoaded = data.wordsLoaded ?? 0;
      showToast.success(`Bad word filter reloaded successfully! (${wordsLoaded} words active)`);
      fetchStats();
      fetchSystemHealth();
    } catch (err: any) {
      showToast.error("Failed to reload bad word filter.");
    } finally {
      setExecutingAction(null);
    }
  };

  // System quick actions API integrations
  const handleRunCleanup = async () => {
    setExecutingAction("cleanup");
    try {
      await axiosInstance.post("/api/posts/cleanup/files");
      showToast.success("File cleanup queue processed successfully");
      fetchStats();
      fetchSystemHealth();
    } catch (err: any) {
      showToast.error("Failed to process file cleanup queue.");
    } finally {
      setExecutingAction(null);
    }
  };

  const handleResetCounters = async () => {
    setExecutingAction("reset");
    try {
      await axiosInstance.post("/api/admin/communities/reset-counters");
      showToast.success("Weekly community engagement counters reset successfully");
      fetchStats();
      fetchSystemHealth();
    } catch (err: any) {
      showToast.error("Failed to reset community counters.");
    } finally {
      setExecutingAction(null);
    }
  };

  const handleTriggerNotifCleanup = async () => {
    setExecutingAction("notif-cleanup");
    try {
      await axiosInstance.post("/api/admin/notifications/cleanup");
      showToast.success("Notification cleanup completed (notifications older than 30 days removed)");
      fetchStats();
      fetchSystemHealth();
    } catch (err: any) {
      showToast.error("Failed to trigger notification cleanup.");
    } finally {
      setExecutingAction(null);
    }
  };

  const handleForceEndAllChat = async () => {
    if (!window.confirm("Are you sure you want to terminate ALL active chat sessions? This will disconnect all chatting citizens immediately!")) {
      return;
    }
    setExecutingAction("chat-kill");
    try {
      const res = await axiosInstance.post("/api/admin/chat/force-end-all");
      const msg = res.data?.message || "All active chat sessions terminated.";
      showToast.success(msg);
      fetchStats();
      fetchSystemHealth();
    } catch (err: any) {
      showToast.error("Failed to terminate chat sessions.");
    } finally {
      setExecutingAction(null);
    }
  };

  return (
    <div className="admin-dashboard-root">
      {/* SCOPED CSS STYLES FOR THE PREMIUM DARK INTERFACE */}
      <style>{`
        .admin-dashboard-root {
          --bg: oklch(var(--b1));
          --bg-surface: oklch(var(--b2));
          --bg-card: oklch(var(--b3));
          --bg-card-hover: color-mix(in srgb, oklch(var(--b3)) 90%, oklch(var(--bc)) 10%);
          --accent: var(--p, #1D4ED8);
          --accent-dim: color-mix(in srgb, var(--p, #1D4ED8) 12%, transparent);
          --accent2: #3b82f6;
          --accent2-dim: rgba(59, 130, 246, 0.12);
          --success: oklch(var(--su));
          --success-dim: oklch(var(--su) / 0.15);
          --warn: oklch(var(--wa));
          --warn-dim: oklch(var(--wa) / 0.15);
          --danger: oklch(var(--er));
          --danger-dim: oklch(var(--er) / 0.15);
          --text-primary: oklch(var(--bc));
          --text-secondary: oklch(var(--bc) / 0.7);
          --text-muted: oklch(var(--bc) / 0.4);
          --border: oklch(var(--bc) / 0.08);
          --border-strong: oklch(var(--bc) / 0.15);
          --radius: 10px;
          --radius-lg: 16px;
          --font-head: 'Satoshi', sans-serif;
          --font-body: 'Satoshi', sans-serif;
          --font-mono: monospace;
          --sidebar-w: 220px;
          --topbar-h: 60px;
        }

        /* Force Satoshi font everywhere in the dashboard */
        .admin-dashboard-root,
        .admin-dashboard-root * {
          font-family: 'Satoshi', sans-serif !important;
        }

        .admin-dashboard-root {
          background: var(--bg) !important;
          color: var(--text-primary) !important;
          border-radius: var(--radius-lg);
          border: 1px solid var(--border);
          overflow: hidden;
          margin-top: 10px;
          display: flex;
          height: calc(100dvh - 90px);
          max-height: calc(100dvh - 90px);
          width: 100%;
        }



        .admin-main {
          flex: 1;
          display: flex;
          flex-direction: column;
          min-height: 0;
          overflow: hidden;
        }

        .admin-topbar {
          height: var(--topbar-h);
          background: var(--bg-surface);
          border-bottom: 1px solid var(--border);
          display: flex;
          align-items: center;
          padding: 0 20px;
          gap: 12px;
        }

        .admin-topbar-title {
          font-family: var(--font-head);
          font-size: 17px;
          font-weight: 700;
          flex: 1;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .admin-topbar-search {
          display: flex;
          align-items: center;
          background: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 0 12px;
          gap: 8px;
          height: 36px;
          width: 240px;
          flex-shrink: 0;
        }

        .admin-topbar-search input {
          background: none;
          border: none;
          outline: none;
          color: var(--text-primary);
          font-size: 13px;
          font-family: var(--font-body);
          flex: 1;
          width: 100%;
        }

        .admin-topbar-search input::placeholder { color: var(--text-muted); }

        .admin-topbar-actions { display: flex; gap: 8px; align-items: center; flex-shrink: 0; }

        .admin-icon-btn {
          width: 36px; height: 36px;
          border-radius: 8px;
          background: var(--bg-card);
          border: 1px solid var(--border);
          display: flex; align-items: center; justify-content: center;
          cursor: pointer;
          transition: all 0.15s;
          flex-shrink: 0;
        }
        .admin-icon-btn:hover { border-color: var(--border-strong); background: var(--bg-card-hover); }

        .admin-content {
          padding: 28px;
          flex: 1;
          min-height: 0;
          overflow-y: auto;
          -webkit-overflow-scrolling: touch;
        }

        /* ── MOBILE RESPONSIVE OVERRIDES ── */
        @media (max-width: 767px) {
          .admin-dashboard-root {
            margin-top: 4px;
            height: calc(100dvh - 74px);
            max-height: calc(100dvh - 74px);
            border-radius: 10px;
          }

          .admin-topbar {
            padding: 0 12px;
            gap: 8px;
            height: 54px;
          }

          .admin-topbar-title {
            font-size: 14px;
          }

          .admin-topbar-search {
            display: none;
          }

          .admin-content {
            padding: 16px 14px;
          }

          .admin-stat-grid {
            grid-template-columns: 1fr 1fr;
            gap: 10px;
            margin-bottom: 16px;
          }

          .admin-stat-val {
            font-size: 24px;
          }

          .admin-mini-stat-row {
            grid-template-columns: 1fr 1fr;
            gap: 8px;
          }

          .admin-two-col {
            grid-template-columns: 1fr;
            gap: 14px;
          }

          .admin-card-head {
            padding: 14px 16px 10px;
            flex-wrap: wrap;
            gap: 8px;
          }

          .admin-card-body {
            padding: 14px 16px;
          }

          .admin-table-scroll {
            overflow-x: auto;
            -webkit-overflow-scrolling: touch;
          }

          .admin-data-table {
            min-width: 540px;
          }

          .admin-form-grid {
            grid-template-columns: 1fr;
          }

          .admin-section-header {
            flex-direction: column;
            align-items: flex-start;
            gap: 8px;
          }

          .admin-filter-row {
            gap: 6px;
          }

          .admin-filter-chip {
            padding: 5px 10px;
            font-size: 11px;
          }

          .admin-section-title {
            font-size: 15px;
          }

          .admin-btn-actions {
            flex-wrap: wrap;
          }

          .admin-posts-panel {
            width: 100vw;
          }
        }

        @media (max-width: 400px) {
          .admin-stat-grid {
            grid-template-columns: 1fr;
          }
          .admin-mini-stat-row {
            grid-template-columns: 1fr;
          }
        }

        .admin-stat-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
          gap: 16px;
          margin-bottom: 28px;
        }

        .admin-stat-card {
          background: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: var(--radius-lg);
          padding: 20px;
          position: relative;
          overflow: hidden;
          transition: border-color 0.2s, transform 0.2s;
          cursor: default;
        }

        .admin-stat-card::before {
          content: '';
          position: absolute;
          top: 0; left: 0; right: 0;
          height: 2px;
        }

        .admin-stat-card.orange::before { background: var(--accent); }
        .admin-stat-card.blue::before { background: var(--accent2); }
        .admin-stat-card.green::before { background: var(--success); }
        .admin-stat-card.yellow::before { background: var(--warn); }

        .admin-stat-card:hover { border-color: var(--border-strong); transform: translateY(-1px); }

        .admin-stat-label {
          font-size: 11px;
          font-family: var(--font-mono);
          text-transform: uppercase;
          letter-spacing: 0.8px;
          color: var(--text-muted);
          margin-bottom: 10px;
        }

        .admin-stat-val {
          font-family: var(--font-head);
          font-size: 32px;
          font-weight: 800;
          line-height: 1;
          margin-bottom: 8px;
        }

        .admin-stat-card.orange .admin-stat-val { color: var(--accent); }
        .admin-stat-card.blue .admin-stat-val { color: var(--accent2); }
        .admin-stat-card.green .admin-stat-val { color: var(--success); }
        .admin-stat-card.yellow .admin-stat-val { color: var(--warn); }

        .admin-stat-delta {
          font-size: 12px;
          color: var(--text-muted);
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .admin-delta-up { color: var(--success); }
        .admin-delta-down { color: var(--danger); }

        .admin-two-col {
          display: grid;
          grid-template-columns: 1fr 380px;
          gap: 20px;
          margin-bottom: 20px;
        }

        @media (max-width: 1200px) {
          .admin-two-col { grid-template-columns: 1fr; }
        }

        .admin-card {
          background: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: var(--radius-lg);
          overflow: hidden;
          margin-bottom: 20px;
        }

        .admin-card-head {
          padding: 18px 22px 14px;
          border-bottom: 1px solid var(--border);
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 12px;
        }

        .admin-card-title {
          font-family: var(--font-head);
          font-size: 14px;
          font-weight: 700;
          color: var(--text-primary);
        }

        .admin-card-subtitle {
          font-size: 12px;
          color: var(--text-muted);
          margin-top: 2px;
          font-family: var(--font-mono);
        }

        .admin-card-body { padding: 18px 22px; }

        .admin-activity-item {
          display: flex;
          align-items: flex-start;
          gap: 12px;
          padding: 12px 0;
          border-bottom: 1px solid var(--border);
        }

        .admin-activity-item:last-child { border-bottom: none; }

        .admin-activity-dot {
          width: 8px; height: 8px;
          border-radius: 50%;
          margin-top: 5px;
          flex-shrink: 0;
        }

        .admin-activity-text {
          font-size: 13px;
          color: var(--text-secondary);
          flex: 1;
          line-height: 1.5;
        }

        .admin-activity-text strong { color: var(--text-primary); font-weight: 500; }

        .admin-activity-time {
          font-size: 11px;
          color: var(--text-muted);
          font-family: var(--font-mono);
          white-space: nowrap;
        }

        .admin-qstat { display: flex; flex-direction: column; gap: 0; }

        .admin-qstat-row {
          display: flex;
          align-items: center;
          padding: 12px 0;
          border-bottom: 1px solid var(--border);
          gap: 10px;
        }

        .admin-qstat-row:last-child { border-bottom: none; }

        .admin-qstat-icon {
          width: 32px; height: 32px;
          border-radius: 8px;
          display: flex; align-items: center; justify-content: center;
          font-size: 14px;
          flex-shrink: 0;
        }

        .admin-qstat-label { flex: 1; font-size: 13px; color: var(--text-secondary); }
        .admin-qstat-value { font-family: var(--font-head); font-size: 16px; font-weight: 700; }

        .admin-data-table {
          width: 100%;
          min-width: 760px;
          border-collapse: collapse;
        }

        .admin-table-scroll {
          max-height: min(56vh, 520px);
          overflow: auto;
          scrollbar-gutter: stable;
          -webkit-overflow-scrolling: touch;
        }

        .admin-data-table th {
          font-size: 10.5px;
          font-family: var(--font-mono);
          text-transform: uppercase;
          letter-spacing: 0.8px;
          color: var(--text-muted);
          padding: 10px 14px;
          text-align: left;
          border-bottom: 1px solid var(--border);
          white-space: nowrap;
        }

        .admin-data-table td {
          padding: 12px 14px;
          font-size: 13px;
          color: var(--text-secondary);
          border-bottom: 1px solid var(--border);
          vertical-align: middle;
        }

        .admin-data-table tr:last-child td { border-bottom: none; }

        .admin-data-table tr:hover td { background: rgba(255,255,255,0.02); }

        .admin-td-name { color: var(--text-primary); font-weight: 500; }

        .admin-badge {
          display: inline-flex;
          align-items: center;
          padding: 3px 9px;
          border-radius: 20px;
          font-size: 11px;
          font-family: var(--font-mono);
          font-weight: 500;
          white-space: nowrap;
        }

        .admin-badge-admin { background: rgba(249,115,22,0.15); color: #fb923c; border: 1px solid rgba(249,115,22,0.25); }
        .admin-badge-dept  { background: rgba(59,130,246,0.15); color: #60a5fa; border: 1px solid rgba(59,130,246,0.25); }
        .admin-badge-user  { background: rgba(34,197,94,0.12); color: #4ade80; border: 1px solid rgba(34,197,94,0.2); }
        .admin-badge-active { background: rgba(34,197,94,0.12); color: #4ade80; border: 1px solid rgba(34,197,94,0.2); }
        .admin-badge-inactive { background: rgba(239,68,68,0.12); color: #f87171; border: 1px solid rgba(239,68,68,0.2); }
        .admin-badge-pending { background: rgba(234,179,8,0.12); color: #facc15; border: 1px solid rgba(234,179,8,0.2); }

        .admin-form-section { margin-bottom: 24px; }

        .admin-form-grid {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 14px;
        }
        @media (max-width: 768px) {
          .admin-form-grid { grid-template-columns: 1fr; }
        }

        .admin-form-group { display: flex; flex-direction: column; gap: 6px; }
        .admin-form-group.full { grid-column: 1 / -1; }

        .admin-form-label {
          font-size: 11px;
          font-family: var(--font-mono);
          text-transform: uppercase;
          letter-spacing: 0.7px;
          color: var(--text-muted);
        }

        .admin-form-label span { color: var(--accent); }

        .admin-form-input, .admin-form-select {
          background: var(--bg);
          border: 1px solid var(--border-strong);
          border-radius: 8px;
          padding: 10px 14px;
          color: var(--text-primary);
          font-family: var(--font-body);
          font-size: 14px;
          outline: none;
          transition: border-color 0.15s;
          width: 100%;
        }

        .admin-form-input:focus, .admin-form-select:focus {
          border-color: var(--accent);
          box-shadow: 0 0 0 3px rgba(249,115,22,0.12);
        }

        .admin-form-hint {
          font-size: 11px;
          color: var(--text-muted);
          font-family: var(--font-mono);
        }

        .admin-form-divider {
          border: none;
          border-top: 1px solid var(--border);
          margin: 22px 0;
        }

        .admin-form-section-title {
          font-family: var(--font-head);
          font-size: 13px;
          font-weight: 700;
          color: var(--text-secondary);
          text-transform: uppercase;
          letter-spacing: 1px;
          margin-bottom: 16px;
        }

        .admin-password-wrap { position: relative; }

        .admin-password-wrap .admin-form-input { padding-right: 42px; }

        .admin-pw-toggle {
          position: absolute;
          right: 12px; top: 50%;
          transform: translateY(-50%);
          cursor: pointer;
          color: var(--text-muted);
          font-size: 12px;
          font-family: var(--font-mono);
          background: none; border: none;
          transition: color 0.15s;
        }

        .admin-pw-toggle:hover { color: var(--text-secondary); }

        .admin-btn {
          display: inline-flex;
          align-items: center;
          gap: 8px;
          padding: 10px 20px;
          border-radius: 8px;
          font-family: var(--font-body);
          font-size: 13.5px;
          font-weight: 500;
          cursor: pointer;
          transition: all 0.15s;
          border: none;
          outline: none;
        }

        .admin-btn-primary {
          background: var(--accent);
          color: #fff;
        }

        .admin-btn-primary:hover { background: #ea6c0d; transform: translateY(-1px); }
        .admin-btn-primary:active { transform: scale(0.98); }

        .admin-btn-secondary {
          background: var(--bg-card-hover);
          border: 1px solid var(--border-strong);
          color: var(--text-secondary);
        }

        .admin-btn-secondary:hover { border-color: var(--border-strong); color: var(--text-primary); background: rgba(255,255,255,0.06); }

        .admin-btn-danger {
          background: rgba(239,68,68,0.15);
          border: 1px solid rgba(239,68,68,0.3);
          color: var(--danger);
        }

        .admin-btn-danger:hover { background: rgba(239,68,68,0.25); }

        .admin-btn-sm {
          padding: 6px 12px;
          font-size: 12px;
        }

        .admin-btn-actions {
          display: flex;
          gap: 10px;
          padding-top: 8px;
          align-items: center;
        }

        .admin-alert {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 13px 16px;
          border-radius: 9px;
          margin-bottom: 18px;
          font-size: 13.5px;
        }

        .admin-alert-success { background: rgba(34,197,94,0.1); border: 1px solid rgba(34,197,94,0.25); color: #4ade80; }
        .admin-alert-error   { background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.25); color: #f87171; }
        .admin-alert-info    { background: rgba(59,130,246,0.1); border: 1px solid rgba(59,130,246,0.25); color: #60a5fa; }

        .admin-mini-stat-row {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 12px;
          margin-bottom: 20px;
        }

        .admin-mini-stat {
          background: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: var(--radius);
          padding: 14px 16px;
          text-align: center;
        }

        .admin-mini-val {
          font-family: var(--font-head);
          font-size: 24px;
          font-weight: 800;
          margin-bottom: 4px;
        }

        .admin-mini-label {
          font-size: 11px;
          font-family: var(--font-mono);
          color: var(--text-muted);
          text-transform: uppercase;
          letter-spacing: 0.6px;
        }

        .admin-section-header {
          display: flex;
          align-items: baseline;
          justify-content: space-between;
          margin-bottom: 16px;
        }

        .admin-section-title {
          font-family: var(--font-head);
          font-size: 18px;
          font-weight: 800;
          color: var(--text-primary);
        }

        .admin-section-sub {
          font-size: 12px;
          color: var(--text-muted);
          font-family: var(--font-mono);
        }

        .admin-filter-row {
          display: flex;
          gap: 10px;
          align-items: center;
          margin-bottom: 16px;
          flex-wrap: wrap;
        }

        .admin-filter-chip {
          padding: 6px 14px;
          border-radius: 20px;
          font-size: 12px;
          font-family: var(--font-mono);
          cursor: pointer;
          border: 1px solid var(--border);
          background: var(--bg-card);
          color: var(--text-muted);
          transition: all 0.15s;
        }

        .admin-filter-chip.active {
          background: rgba(249,115,22,0.15);
          border-color: rgba(249,115,22,0.4);
          color: var(--accent);
        }

        .admin-filter-chip:hover:not(.active) {
          border-color: var(--border-strong);
          color: var(--text-secondary);
        }

        .admin-u-avatar {
          width: 30px; height: 30px;
          border-radius: 50%;
          display: flex; align-items: center; justify-content: center;
          font-size: 11px;
          font-weight: 700;
          flex-shrink: 0;
        }

        .admin-ua-orange { background: rgba(249,115,22,0.2); color: var(--accent); }
        .admin-ua-blue   { background: rgba(59,130,246,0.2); color: var(--accent2); }
        .admin-ua-green  { background: rgba(34,197,94,0.2); color: var(--success); }
        .admin-ua-purple { background: rgba(168,85,247,0.2); color: #c084fc; }

        .admin-u-info { flex: 1; min-width: 0; }
        .admin-u-name { font-size: 13px; font-weight: 500; color: var(--text-primary); }
        .admin-u-sub  { font-size: 11px; color: var(--text-muted); font-family: var(--font-mono); }

        .admin-pincode-tag {
          font-family: var(--font-mono);
          font-size: 12px;
          background: rgba(59,130,246,0.1);
          border: 1px solid rgba(59,130,246,0.2);
          color: #60a5fa;
          padding: 3px 8px;
          border-radius: 5px;
        }

        .admin-progress-bar {
          height: 5px;
          background: rgba(255,255,255,0.07);
          border-radius: 10px;
          overflow: hidden;
          margin-top: 8px;
        }

        .admin-progress-fill {
          height: 100%;
          border-radius: 10px;
          transition: width 0.6s ease;
        }

        .admin-tag-grid {
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
          margin-top: 10px;
        }

        .admin-tag {
          background: rgba(255,255,255,0.05);
          border: 1px solid var(--border);
          border-radius: 6px;
          padding: 4px 10px;
          font-size: 12px;
          font-family: var(--font-mono);
          color: var(--text-secondary);
        }

        /* ── POSTS SLIDE-OVER ── */
        .admin-posts-panel {
          position: fixed;
          top: 0; right: -580px;
          width: 580px;
          max-width: 96vw;
          height: 100vh;
          background: #ffffff !important;
          border-left: 1px solid var(--border-strong);
          z-index: 2000;
          display: flex;
          flex-direction: column;
          transition: right 0.3s cubic-bezier(0.22,1,0.36,1);
          box-shadow: -20px 0 60px rgba(0,0,0,0.5);
        }

        [data-theme="dark"] .admin-posts-panel {
          background: #181b24 !important;
        }

        .admin-posts-panel.open { right: 0; }

        .admin-posts-panel-backdrop {
          position: fixed;
          inset: 0;
          background: rgba(0,0,0,0.5);
          backdrop-filter: blur(3px);
          z-index: 1999;
          opacity: 0;
          pointer-events: none;
          transition: opacity 0.3s;
        }

        .admin-posts-panel-backdrop.open { opacity: 1; pointer-events: auto; }

        .admin-posts-panel-head {
          padding: 20px 24px 16px;
          border-bottom: 1px solid var(--border);
          display: flex;
          flex-direction: column;
          gap: 14px;
          flex-shrink: 0;
        }

        .admin-posts-panel-title-row {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 12px;
        }

        .admin-posts-panel-user {
          display: flex;
          align-items: center;
          gap: 12px;
        }

        .admin-posts-panel-avatar {
          width: 40px; height: 40px;
          border-radius: 50%;
          background: linear-gradient(135deg, var(--accent), #1e40af);
          display: flex; align-items: center; justify-content: center;
          font-family: var(--font-head);
          font-size: 14px; font-weight: 800;
          color: #fff;
          flex-shrink: 0;
        }

        .admin-posts-panel-meta { flex: 1; }
        .admin-posts-panel-name { font-family: var(--font-head); font-size: 16px; font-weight: 800; }
        .admin-posts-panel-sub  { font-size: 11px; color: var(--text-muted); font-family: var(--font-mono); margin-top: 2px; }

        .admin-posts-panel-stats {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 10px;
        }

        .admin-posts-panel-stat {
          background: rgba(255,255,255,0.04);
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 8px 12px;
          text-align: center;
        }

        .admin-posts-panel-stat-val { font-family: var(--font-head); font-size: 18px; font-weight: 800; }
        .admin-posts-panel-stat-lbl { font-size: 10px; font-family: var(--font-mono); color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; margin-top: 2px; }

        .admin-posts-panel-filter {
          display: flex;
          gap: 6px;
          padding: 0 24px 12px;
          border-bottom: 1px solid var(--border);
          flex-shrink: 0;
          flex-wrap: wrap;
        }

        .admin-posts-panel-body {
          flex: 1;
          overflow-y: auto;
          padding: 16px 24px;
          display: flex;
          flex-direction: column;
          gap: 12px;
        }

        .admin-post-card {
          background: var(--bg-card);
          border: 1px solid var(--border);
          border-radius: var(--radius);
          padding: 14px 16px;
          transition: border-color 0.15s;
        }

        .admin-post-card:hover { border-color: var(--border-strong); }

        .admin-post-card-head {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 8px;
        }

        .admin-post-type-tag {
          font-size: 10px;
          font-family: var(--font-mono);
          text-transform: uppercase;
          letter-spacing: 0.7px;
          padding: 2px 8px;
          border-radius: 20px;
          background: rgba(59,130,246,0.12);
          color: #60a5fa;
          border: 1px solid rgba(59,130,246,0.2);
        }

        .admin-post-type-tag.social { background: rgba(168,85,247,0.12); color: #c084fc; border-color: rgba(168,85,247,0.2); }
        .admin-post-type-tag.broadcast { background: rgba(249,115,22,0.12); color: #fb923c; border-color: rgba(249,115,22,0.2); }
        .admin-post-type-tag.resolved { background: rgba(34,197,94,0.12); color: #4ade80; border-color: rgba(34,197,94,0.2); }
        .admin-post-type-tag.poll { background: rgba(234,179,8,0.12); color: #facc15; border-color: rgba(234,179,8,0.2); }

        .admin-post-card-body { font-size: 13px; color: var(--text-secondary); line-height: 1.55; margin-bottom: 10px; }
        .admin-post-card-body strong { color: var(--text-primary); }

        .admin-post-card-foot {
          display: flex;
          align-items: center;
          gap: 14px;
          font-size: 11px;
          font-family: var(--font-mono);
          color: var(--text-muted);
        }

        .admin-post-card-foot span { display: flex; align-items: center; gap: 4px; }

        .admin-posts-panel-empty {
          text-align: center;
          padding: 60px 20px;
          color: var(--text-muted);
          font-family: var(--font-mono);
          font-size: 13px;
        }

        .admin-posts-panel-loading {
          display: flex;
          flex-direction: column;
          gap: 12px;
          padding: 16px 0;
        }

        @keyframes skeleton-shimmer {
          0% {
            background-position: 200% 0;
          }
          100% {
            background-position: -200% 0;
          }
        }

        .admin-skeleton {
          background: linear-gradient(90deg, rgba(0,0,0,0.06) 25%, rgba(0,0,0,0.12) 37%, rgba(0,0,0,0.06) 63%);
          background-size: 200% 100%;
          animation: skeleton-shimmer 1.4s ease infinite;
          border-radius: 4px;
          display: inline-block;
        }

        [data-theme="dark"] .admin-skeleton {
          background: linear-gradient(90deg, rgba(255,255,255,0.06) 25%, rgba(255,255,255,0.12) 37%, rgba(255,255,255,0.06) 63%);
          background-size: 200% 100%;
        }

      `}</style>



        



      {/* ══ MAIN CONTENT COLUMN ══ */}
      <div className="admin-main">
        {/* TOPBAR */}
        <header className="admin-topbar">
          <div className="admin-topbar-title capitalize">{activeTab.replace("-", " ")} Panel</div>

          <div className="admin-topbar-search">
            <Search size={14} className="text-[#4a5270]" />
            <input
              type="text"
              placeholder="Search databases..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
            />
          </div>

          <div className="admin-topbar-actions">
            <button className="admin-icon-btn notif-dot" title="Reload Bad Words List" onClick={handleReloadBadWords}>
              <RefreshCw size={16} className="text-secondary" />
            </button>
            <button className="admin-icon-btn" title="Refresh Live Data" onClick={fetchStats} disabled={loadingStats}>
              <TrendingUp size={16} className={loadingStats ? "animate-spin text-accent" : "text-secondary"} />
            </button>
          </div>
        </header>

        {/* VIEWPORTS */}
        <div className="admin-content">
          <AnimatePresence mode="wait">
            
            {/* ──────────── VIEW: DASHBOARD ──────────── */}
            {activeTab === "dashboard" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">Platform Live Overview</div>
                    <div className="admin-section-sub">Dynamic sync with Govlyx microservices</div>
                  </div>
                  <button className="admin-btn admin-btn-secondary admin-btn-sm" onClick={fetchStats}>
                    <RefreshCw size={13} className="mr-1" /> Refresh Stats
                  </button>
                </div>

                {/* STAT GRID */}
                <div className="admin-stat-grid">
                  {(() => {
                    const allLiveOverviewLoaded = overviewStats !== null && liveSessions !== null;
                    return (
                      <>
                        <div className="admin-stat-card orange">
                          <div className="admin-stat-label">Total Citizens</div>
                          <div className="admin-stat-val">
                            {allLiveOverviewLoaded ? overviewStats.totalCitizens.toLocaleString() : "..."}
                          </div>
                          <div className="text-xs text-[var(--text-secondary)] mt-1">Total registered citizen accounts</div>
                        </div>
                        <div className="admin-stat-card blue">
                          <div className="admin-stat-label">Active Chat Sessions</div>
                          <div className="admin-stat-val">
                            {allLiveOverviewLoaded ? liveSessions.toLocaleString() : "..."}
                          </div>
                          <div className="text-xs text-[var(--text-secondary)] mt-1">Live anonymous chat socket sessions</div>
                        </div>
                        <div className="admin-stat-card green">
                          <div className="admin-stat-label">Active Issues</div>
                          <div className="admin-stat-val">
                            {allLiveOverviewLoaded ? overviewStats.activeIssues.toLocaleString() : "..."}
                          </div>
                          <div className="text-xs text-[var(--text-secondary)] mt-1">Active citizen feedback posts</div>
                        </div>
                        <div className="admin-stat-card yellow">
                          <div className="admin-stat-label">Resolved Posts</div>
                          <div className="admin-stat-val">
                            {allLiveOverviewLoaded ? overviewStats.resolvedPosts.toLocaleString() : "..."}
                          </div>
                          <div className="text-xs text-[var(--text-secondary)] mt-1">Total citizen posts marked resolved</div>
                        </div>
                      </>
                    );
                  })()}
                </div>

                <div className="admin-two-col">
                  {/* Recent Activity Column */}
                  <div className="admin-card">
                    <div className="admin-card-head">
                      <div>
                        <div className="admin-card-title">Recent Activity</div>
                        <div className="admin-card-subtitle">Live platform events</div>
                      </div>
                    </div>
                    <div className="admin-card-body">
                      {recentActivities.length === 0 ? (
                        <div className="py-12 text-center text-[var(--text-secondary)] opacity-60 italic text-sm">
                          No recent activity recorded on server.
                        </div>
                      ) : (
                        <div className="space-y-4">
                          {recentActivities.map((act: any, idx: number) => (
                            <div key={idx} className="admin-activity-item">
                              <div className="admin-activity-dot" style={{ background: act.color || "var(--accent)" }} />
                              <div className="admin-activity-text" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(act.description) }} />
                              <div className="admin-activity-time">{act.timeAgo || "just now"}</div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Platform Metrics Column */}
                  <div className="admin-card">
                    <div className="admin-card-head">
                      <div>
                        <div className="admin-card-title">Platform Metrics</div>
                      </div>
                    </div>
                    <div className="admin-card-body">
                      <div className="admin-qstat">
                        <div className="admin-qstat-row">
                          <div className="admin-qstat-icon" style={{ background: "rgba(249,115,22,0.12)" }}><Globe size={16} className="text-[#f97316]" /></div>
                          <div className="admin-qstat-label">Country-wide Broadcasts</div>
                          <div className="admin-qstat-value" style={{ color: "var(--accent)" }}>
                            {broadcastStats !== null ? (broadcastStats.broadcastsCOUNTRY ?? 0).toLocaleString() : "..."}
                          </div>
                        </div>
                        <div className="admin-qstat-row">
                          <div className="admin-qstat-icon" style={{ background: "rgba(59,130,246,0.12)" }}><Landmark size={16} className="text-[#3b82f6]" /></div>
                          <div className="admin-qstat-label">State-level Broadcasts</div>
                          <div className="admin-qstat-value" style={{ color: "var(--accent2)" }}>
                            {broadcastStats !== null ? (broadcastStats.broadcastsSTATE ?? 0).toLocaleString() : "..."}
                          </div>
                        </div>
                        <div className="admin-qstat-row">
                          <div className="admin-qstat-icon" style={{ background: "rgba(34,197,94,0.12)" }}><Map size={16} className="text-[#22c55e]" /></div>
                          <div className="admin-qstat-label">District-level Broadcasts</div>
                          <div className="admin-qstat-value" style={{ color: "var(--success)" }}>
                            {broadcastStats !== null ? (broadcastStats.broadcastsDISTRICT ?? 0).toLocaleString() : "..."}
                          </div>
                        </div>
                        <div className="admin-qstat-row">
                          <div className="admin-qstat-icon" style={{ background: "rgba(234,179,8,0.12)" }}><MapPin size={16} className="text-[#eab308]" /></div>
                          <div className="admin-qstat-label">Area (Pincode) Broadcasts</div>
                          <div className="admin-qstat-value" style={{ color: "var(--warn)" }}>
                            {broadcastStats !== null ? (broadcastStats.broadcastsAREA ?? 0).toLocaleString() : "..."}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Bottom Row - Recently Registered Departments */}
                <div className="admin-card">
                  <div className="admin-card-head">
                    <div>
                      <div className="admin-card-title">Recently Registered Departments</div>
                    </div>
                    <button className="admin-btn admin-btn-secondary admin-btn-sm" onClick={() => setActiveTab("departments")}>View All</button>
                  </div>
                  <div className="admin-card-body p-0">
                    <div className="admin-table-scroll">
                      <table className="admin-data-table">
                        <thead>
                          <tr>
                            <th>USERNAME</th>
                            <th>EMAIL</th>
                            <th>PINCODE</th>
                            <th>STATUS</th>
                            <th>REGISTERED</th>
                          </tr>
                        </thead>
                        <tbody>
                          {loadingDepts ? (
                            <tr>
                              <td colSpan={5} className="text-center py-4 text-sm text-[var(--text-secondary)]">
                                <div className="flex items-center justify-center gap-2">
                                  <RefreshCw className="animate-spin text-[var(--accent)]" size={14} />
                                  <span>Loading departments from database...</span>
                                </div>
                              </td>
                            </tr>
                          ) : deptsDb.length === 0 ? (
                            <tr>
                              <td colSpan={5} className="text-center py-6 text-sm text-[var(--text-secondary)] opacity-60">
                                No departments registered on the system.
                              </td>
                            </tr>
                          ) : (
                            deptsDb.slice(0, 5).map(dept => (
                              <tr key={dept.id} className="user-row cursor-pointer" onClick={() => handleOpenDetails({ id: dept.id, username: dept.name, email: dept.email, pincode: dept.pincode, role: "ROLE_DEPARTMENT", status: dept.status, regDate: dept.regDate })}>
                                <td>
                                  <div className="flex items-center gap-2">
                                    <div className="admin-u-avatar admin-ua-blue">{dept.name.charAt(0).toUpperCase()}</div>
                                    <div className="admin-td-name">{dept.name}</div>
                                  </div>
                                </td>
                                <td>{dept.email}</td>
                                <td><span className="admin-pincode-tag">{dept.pincode}</span></td>
                                <td>
                                  <span className={`admin-badge ${dept.status === "Active" ? "admin-badge-active" : "admin-badge-pending"}`}>
                                    {dept.status}
                                  </span>
                                </td>
                                <td>{formatRegDate(dept.regDate)}</td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: REGISTER DEPARTMENT ──────────── */}
            {activeTab === "reg-dept" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="max-w-3xl"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">Onboard Government Department</div>
                  </div>
                </div>

                <div className="admin-card">
                  <div className="admin-card-head">
                    <div>
                      <div className="admin-card-title">Create Department Authority Account</div>
                      <div className="admin-card-subtitle">Verify department scope and pincode mapping</div>
                    </div>
                    <span className="admin-badge admin-badge-dept">ROLE_DEPARTMENT</span>
                  </div>
                  <div className="admin-card-body">
                    <form onSubmit={handleRegisterDept} className="space-y-6">
                      <div className="admin-form-section-title">Identity Parameters</div>
                      <div className="admin-form-grid">
                        <div className="admin-form-group">
                          <label className="admin-form-label">Department Unit Name <span>*</span></label>
                          <input
                            type="text"
                            required
                            className="admin-form-input"
                            placeholder="e.g. WaterDeptMumbai"
                            value={deptForm.name}
                            onChange={e => setDeptForm({ ...deptForm, name: e.target.value })}
                          />
                          <div className="admin-form-hint">Used for display and citizen tags</div>
                        </div>

                        <div className="admin-form-group">
                          <label className="admin-form-label">Official Gov Email <span>*</span></label>
                          <input
                            type="email"
                            required
                            className="admin-form-input"
                            placeholder="dept.mumbai@gov.in"
                            value={deptForm.email}
                            onChange={e => setDeptForm({ ...deptForm, email: e.target.value })}
                          />
                        </div>
                      </div>

                      <hr className="admin-form-divider" />

                      <div className="admin-form-section-title">Jurisdiction</div>
                      <div className="admin-form-grid">
                        <div className="admin-form-group full">
                          <label className="admin-form-label">Primary Pincode (Area) <span>*</span></label>
                          <input
                            type="text"
                            required
                            className="admin-form-input"
                            placeholder="e.g. 400001"
                            maxLength={6}
                            value={deptForm.pincode}
                            onChange={e => setDeptForm({ ...deptForm, pincode: e.target.value.replace(/\D/g, "") })}
                          />
                          <div className="admin-form-hint">Indian pincode for geo-tag tracking</div>
                        </div>
                      </div>

                      <hr className="admin-form-divider" />

                      <div className="admin-form-section-title">Access Password</div>
                      <div className="admin-form-grid">
                        <div className="admin-form-group">
                          <label className="admin-form-label">Password <span>*</span></label>
                          <div className="admin-password-wrap">
                            <input
                              type={showDeptPw ? "text" : "password"}
                              required
                              className="admin-form-input"
                              placeholder="••••••••"
                              value={deptForm.password}
                              onChange={e => setDeptForm({ ...deptForm, password: e.target.value })}
                            />
                            <button
                              type="button"
                              className="admin-pw-toggle"
                              onClick={() => setShowDeptPw(!showDeptPw)}
                            >
                              {showDeptPw ? <EyeOff size={15} /> : <Eye size={15} />}
                            </button>
                          </div>
                        </div>

                        <div className="admin-form-group">
                          <label className="admin-form-label">Confirm Password <span>*</span></label>
                          <div className="admin-password-wrap">
                            <input
                              type={showDeptConf ? "text" : "password"}
                              required
                              className="admin-form-input"
                              placeholder="••••••••"
                              value={deptForm.confirmPassword}
                              onChange={e => setDeptForm({ ...deptForm, confirmPassword: e.target.value })}
                            />
                            <button
                              type="button"
                              className="admin-pw-toggle"
                              onClick={() => setShowDeptConf(!showDeptConf)}
                            >
                              {showDeptConf ? <EyeOff size={15} /> : <Eye size={15} />}
                            </button>
                          </div>
                        </div>
                      </div>

                      <hr className="admin-form-divider" />

                      <div className="admin-form-section-title">Permissions Provisioned</div>
                      <div className="admin-tag-grid mb-6">
                        <span className="admin-tag">Publish Broadcasts</span>
                        <span className="admin-tag">Resolve Issues</span>
                        <span className="admin-tag">View User Distribution</span>
                        <span className="admin-tag">View Broadcast Analytics</span>
                        <span className="admin-tag">Publish Country-wide Broadcasts</span>
                        <span className="admin-tag">View Department Accounts</span>
                      </div>

                      <div className="admin-btn-actions">
                        <button
                          type="submit"
                          className="admin-btn admin-btn-primary"
                          disabled={registeringDept}
                        >
                          <UserPlus size={16} /> {registeringDept ? "Registering..." : "Onboard Department"}
                        </button>
                        <button
                          type="button"
                          className="admin-btn admin-btn-secondary"
                          onClick={() => setDeptForm({
                            name: "", email: "", password: "", confirmPassword: "", pincode: ""
                          })}
                        >
                          Clear
                        </button>
                      </div>
                    </form>
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: REGISTER ADMIN ──────────── */}
            {activeTab === "reg-admin" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="max-w-3xl"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">Register Platform Admin</div>
                  </div>
                </div>

                <div className="admin-card">
                  <div className="admin-card-head">
                    <div>
                      <div className="admin-card-title">Create Admin Authority Account</div>
                      <div className="admin-card-subtitle">Grants full control dashboard panels</div>
                    </div>
                    <span className="admin-badge admin-badge-admin">ROLE_ADMIN</span>
                  </div>
                  <div className="admin-card-body">
                    <div className="admin-alert admin-alert-error mb-4">
                      <ShieldCheck size={16} className="shrink-0" />
                      <div>Admin accounts have unrestricted platform access. Onboard only authorized personnel.</div>
                    </div>

                    <form onSubmit={handleRegisterAdmin} className="space-y-6">
                      <div className="admin-form-section-title">Identity &amp; Account Parameters</div>
                      <div className="admin-form-grid">
                        <div className="admin-form-group">
                          <label className="admin-form-label">Admin Username <span>*</span></label>
                          <input
                            type="text"
                            required
                            className="admin-form-input"
                            placeholder="e.g. AdminRajkumar"
                            value={adminForm.username}
                            onChange={e => setAdminForm({ ...adminForm, username: e.target.value })}
                          />
                          <div className="admin-form-hint">Must be unique across the platform</div>
                        </div>

                        <div className="admin-form-group">
                          <label className="admin-form-label">Official Email <span>*</span></label>
                          <input
                            type="email"
                            required
                            className="admin-form-input"
                            placeholder="admin@govlyx.com"
                            value={adminForm.email}
                            onChange={e => setAdminForm({ ...adminForm, email: e.target.value })}
                          />
                        </div>
                      </div>

                      <div className="admin-form-grid">
                        <div className="admin-form-group">
                          <label className="admin-form-label">Pincode (Optional)</label>
                          <input
                            type="text"
                            className="admin-form-input"
                            placeholder="6-digit pincode"
                            maxLength={6}
                            value={adminForm.pincode}
                            onChange={e => setAdminForm({ ...adminForm, pincode: e.target.value.replace(/\D/g, "") })}
                          />
                        </div>
                      </div>

                      <hr className="admin-form-divider" />

                      <div className="admin-form-section-title">Access Password</div>
                      <div className="admin-form-grid">
                        <div className="admin-form-group">
                          <label className="admin-form-label">Password <span>*</span></label>
                          <div className="admin-password-wrap">
                            <input
                              type={showAdminPw ? "text" : "password"}
                              required
                              className="admin-form-input"
                              placeholder="••••••••"
                              value={adminForm.password}
                              onChange={e => setAdminForm({ ...adminForm, password: e.target.value })}
                            />
                            <button
                              type="button"
                              className="admin-pw-toggle"
                              onClick={() => setShowAdminPw(!showAdminPw)}
                            >
                              {showAdminPw ? <EyeOff size={15} /> : <Eye size={15} />}
                            </button>
                          </div>
                        </div>

                        <div className="admin-form-group">
                          <label className="admin-form-label">Confirm Password <span>*</span></label>
                          <div className="admin-password-wrap">
                            <input
                              type={showAdminConf ? "text" : "password"}
                              required
                              className="admin-form-input"
                              placeholder="••••••••"
                              value={adminForm.confirmPassword}
                              onChange={e => setAdminForm({ ...adminForm, confirmPassword: e.target.value })}
                            />
                            <button
                              type="button"
                              className="admin-pw-toggle"
                              onClick={() => setShowAdminConf(!showAdminConf)}
                            >
                              {showAdminConf ? <EyeOff size={15} /> : <Eye size={15} />}
                            </button>
                          </div>
                        </div>
                      </div>

                      <hr className="admin-form-divider" />

                      <div className="admin-form-section-title">Full Permissions Granted</div>
                      <div className="admin-tag-grid mb-6">
                        <span className="admin-tag">Manage Registrations</span>
                        <span className="admin-tag">Manage Posts & Content</span>
                        <span className="admin-tag">Manage User Accounts</span>
                        <span className="admin-tag">Manage Communities</span>
                        <span className="admin-tag">View Chat Statistics</span>
                        <span className="admin-tag">Execute System Cleanup</span>
                        <span className="admin-tag">Manage Resolutions</span>
                        <span className="admin-tag">Manage User Tagging</span>
                      </div>

                      <div className="admin-btn-actions">
                        <button
                          type="submit"
                          className="admin-btn admin-btn-primary"
                          disabled={registeringAdmin}
                        >
                          <ShieldCheck size={16} /> {registeringAdmin ? "Registering..." : "Register Admin"}
                        </button>
                      </div>
                    </form>
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: ALL USERS ──────────── */}
            {activeTab === "users" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">All Users</div>
                  </div>
                  <div className="flex gap-2">
                    <button
                      className="admin-btn admin-btn-secondary admin-btn-sm"
                      onClick={exportUsersToCSV}
                    >
                      Export CSV
                    </button>
                    <button
                      className="admin-btn admin-btn-primary admin-btn-sm"
                      onClick={() => setActiveTab("reg-dept")}
                    >
                      + Add User
                    </button>
                  </div>
                </div>

                <div className="admin-filter-row">
                  <div className="flex gap-2 flex-wrap flex-1">
                    <span
                      className={`admin-filter-chip ${usersRoleFilter === "all" ? "active" : ""}`}
                      onClick={() => setUsersRoleFilter("all")}
                    >
                      All Roles
                    </span>
                    <span
                      className={`admin-filter-chip ${usersRoleFilter === "admin" ? "active" : ""}`}
                      onClick={() => setUsersRoleFilter("admin")}
                    >
                      Admin
                    </span>
                    <span
                      className={`admin-filter-chip ${usersRoleFilter === "department" ? "active" : ""}`}
                      onClick={() => setUsersRoleFilter("department")}
                    >
                      Department
                    </span>
                    <span
                      className={`admin-filter-chip ${usersRoleFilter === "citizen" ? "active" : ""}`}
                      onClick={() => setUsersRoleFilter("citizen")}
                    >
                      Citizens
                    </span>
                    <span
                      className={`admin-filter-chip ${usersRoleFilter === "active" ? "active" : ""}`}
                      onClick={() => setUsersRoleFilter("active")}
                    >
                      Active
                    </span>
                    <span
                      className={`admin-filter-chip ${usersRoleFilter === "inactive" ? "active" : ""}`}
                      onClick={() => setUsersRoleFilter("inactive")}
                    >
                      Inactive
                    </span>
                  </div>
                  <div className="admin-topbar-search border border-[var(--border-strong)] rounded-lg bg-[var(--bg-card)] px-3 py-1.5 flex items-center gap-2 w-64">
                    <Search size={14} className="text-[#4a5270]" />
                    <input
                      type="text"
                      placeholder="Filter by username / email..."
                      className="bg-transparent border-none outline-none text-xs w-full text-[var(--text-primary)]"
                      value={searchQuery}
                      onChange={e => setSearchQuery(e.target.value)}
                    />
                  </div>
                </div>

                <div className="admin-card">
                  <div className="admin-card-head">
                    <div className="admin-card-title">User Listings</div>
                  </div>
                  <div className="admin-card-body p-0">
                    <div className="admin-table-scroll">
                    <table className="admin-data-table">
                      <thead>
                        <tr>
                          <th>USER</th>
                          <th>EMAIL</th>
                          <th>ROLE</th>
                          <th>PINCODE</th>
                          <th>STATUS</th>
                          <th>JOINED</th>
                          <th>ACTIONS</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(loadingUsers || searchingLive) && (
                          <tr>
                            <td colSpan={7} className="text-center py-4 text-sm text-[var(--text-secondary)]">
                              <div className="flex items-center justify-center gap-2">
                                <RefreshCw className="animate-spin text-[var(--accent)]" size={14} />
                                <span>{loadingUsers ? "Loading users from database..." : "Searching live users..."}</span>
                              </div>
                            </td>
                          </tr>
                        )}
                        {(() => {
                          let localFiltered = usersDb;
                          if (usersRoleFilter === "admin") {
                            localFiltered = localFiltered.filter(u => u.role === "ROLE_ADMIN");
                          } else if (usersRoleFilter === "department") {
                            localFiltered = localFiltered.filter(u => u.role === "ROLE_DEPARTMENT");
                          } else if (usersRoleFilter === "citizen") {
                            localFiltered = localFiltered.filter(u => u.role === "ROLE_USER");
                          } else if (usersRoleFilter === "active") {
                            localFiltered = localFiltered.filter(u => u.status === "Active");
                          } else if (usersRoleFilter === "inactive") {
                            localFiltered = localFiltered.filter(u => u.status !== "Active");
                          }

                          const seen = new Set();
                          const merged = [];
                          for (const u of [...searchResults, ...localFiltered]) {
                            const key = `${u.username}-${u.role}-${u.id}`;
                            if (!seen.has(key)) {
                              seen.add(key);
                              merged.push(u);
                            }
                          }

                          let finalFiltered = merged;
                          if (searchQuery.trim()) {
                            const q = searchQuery.toLowerCase();
                            finalFiltered = finalFiltered.filter(u =>
                              u.username?.toLowerCase().includes(q) ||
                              u.email?.toLowerCase().includes(q) ||
                              u.pincode?.includes(q)
                            );
                          }

                          if (finalFiltered.length === 0) {
                            return (
                              <tr>
                                <td colSpan={7} className="text-center py-8 text-sm text-[var(--text-muted)] italic">
                                  No users found matching filters.
                                </td>
                              </tr>
                            );
                          }

                          return finalFiltered.map(user => (
                            <tr key={user.id} className="user-row cursor-pointer" onClick={() => handleOpenDetails(user)}>
                              <td>
                                <div className="flex items-center gap-2">
                                  <div className={`admin-u-avatar ${
                                    user.role === "ROLE_ADMIN" ? "admin-ua-orange" :
                                    user.role === "ROLE_DEPARTMENT" ? "admin-ua-blue" : "admin-ua-green"
                                  }`}>
                                    {user.username.charAt(0).toUpperCase()}
                                  </div>
                                  <div>
                                    <div className="admin-td-name">{user.username}</div>
                                    <div className="text-[10px] text-[var(--text-muted)]">ID: {user.id}</div>
                                  </div>
                                </div>
                              </td>
                              <td>{user.email}</td>
                              <td>
                                <span className={`admin-badge ${
                                  user.role === "ROLE_ADMIN" ? "admin-badge-admin" :
                                  user.role === "ROLE_DEPARTMENT" ? "admin-badge-dept" : "admin-badge-user"
                                }`}>
                                  {user.role === "ROLE_ADMIN" ? "ADMIN" : user.role === "ROLE_DEPARTMENT" ? "DEPARTMENT" : "CITIZEN"}
                                </span>
                              </td>
                              <td><span className="admin-pincode-tag">{user.pincode}</span></td>
                              <td>
                                <span className={`admin-badge ${user.status === "Active" ? "admin-badge-active" : "admin-badge-inactive"}`}>
                                  {user.status}
                                </span>
                              </td>
                              <td>{formatJoinedDate(user.regDate)}</td>
                              <td>
                                <div className="flex gap-2">
                                  <button
                                    className="admin-btn admin-btn-sm admin-btn-secondary"
                                    onClick={(e) => { e.stopPropagation(); handleOpenDetails(user); }}
                                  >
                                    View Posts
                                  </button>
                                  {user.role !== "ROLE_ADMIN" && user.status === "Active" && (
                                    <button
                                      className="admin-btn admin-btn-sm admin-btn-danger"
                                      onClick={(e) => { e.stopPropagation(); handleDeactivateUser(user.id); }}
                                    >
                                      Delete
                                    </button>
                                  )}
                                </div>
                              </td>
                            </tr>
                          ));
                        })()}
                      </tbody>
                    </table>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: DEPARTMENTS ──────────── */}
            {activeTab === "departments" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">Government Departments</div>
                  </div>
                  <button
                    className="admin-btn admin-btn-primary admin-btn-sm"
                    onClick={() => setActiveTab("reg-dept")}
                  >
                    + Register New
                  </button>
                </div>

                {/* Stats cards row */}
                <div className="grid grid-cols-3 gap-4 mb-6">
                  <div className="admin-mini-stat bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-4 text-center">
                    <div className="admin-mini-val text-[var(--accent2)] font-black text-2xl">{deptsDb.length}</div>
                    <div className="admin-mini-label text-[10px] uppercase font-mono tracking-wider opacity-60 mt-1">Total Depts</div>
                  </div>
                  <div className="admin-mini-stat bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-4 text-center">
                    <div className="admin-mini-val text-[var(--success)] font-black text-2xl">{deptsDb.filter(d => d.status === "Active").length}</div>
                    <div className="admin-mini-label text-[10px] uppercase font-mono tracking-wider opacity-60 mt-1">Active</div>
                  </div>
                  <div className="admin-mini-stat bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-4 text-center">
                    <div className="admin-mini-val text-[var(--warn)] font-black text-2xl">{deptsDb.filter(d => d.status !== "Active").length}</div>
                    <div className="admin-mini-label text-[10px] uppercase font-mono tracking-wider opacity-60 mt-1">Pending</div>
                  </div>
                </div>

                <div className="admin-card">
                  <div className="admin-card-head">
                    <div className="admin-card-title">All Departments</div>
                  </div>
                  <div className="admin-card-body p-0">
                    <div className="admin-table-scroll">
                    <table className="admin-data-table">
                      <thead>
                        <tr>
                          <th>DEPARTMENT</th>
                          <th>EMAIL</th>
                          <th>PINCODE</th>
                          <th>TYPE</th>
                          <th>BROADCASTS</th>
                          <th>STATUS</th>
                          <th>ACTIONS</th>
                        </tr>
                      </thead>
                      <tbody>
                        {loadingDepts ? (
                          <tr>
                            <td colSpan={7} className="text-center py-4 text-sm text-[var(--text-secondary)]">
                              <div className="flex items-center justify-center gap-2">
                                <RefreshCw className="animate-spin text-[var(--accent)]" size={14} />
                                <span>Loading departments from database...</span>
                              </div>
                            </td>
                          </tr>
                        ) : deptsDb.length === 0 ? (
                          <tr>
                            <td colSpan={7} className="text-center py-6 text-sm text-[var(--text-secondary)] opacity-60">
                              No government departments registered.
                            </td>
                          </tr>
                        ) : (
                          deptsDb
                            .filter(d =>
                              d.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                              d.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
                              d.pincode.includes(searchQuery)
                            )
                            .map(dept => (
                              <tr key={dept.id} className="user-row cursor-pointer" onClick={() => handleOpenDetails({ id: dept.id, username: dept.name, email: dept.email, pincode: dept.pincode, role: "ROLE_DEPARTMENT", status: dept.status, regDate: dept.regDate })}>
                                <td>
                                  <div className="flex items-center gap-2">
                                    <div className="admin-u-avatar admin-ua-blue">{dept.name.charAt(0).toUpperCase()}</div>
                                    <div className="admin-td-name">{dept.name}</div>
                                  </div>
                                </td>
                                <td>{dept.email}</td>
                                <td><span className="admin-pincode-tag">{dept.pincode}</span></td>
                                <td>{inferDepartmentType(dept.name)}</td>
                                <td><DepartmentBroadcastCount userId={dept.id} /></td>
                                <td>
                                  <span className={`admin-badge ${
                                    dept.status === "Active" ? "admin-badge-active" :
                                    dept.status === "Pending" ? "admin-badge-pending" : "admin-badge-inactive"
                                  }`}>
                                    {dept.status}
                                  </span>
                                </td>
                                <td>
                                  <div className="flex gap-2" onClick={e => e.stopPropagation()}>
                                    <button
                                      className="admin-btn admin-btn-sm admin-btn-secondary"
                                      onClick={() => handleOpenDetails({ id: dept.id, username: dept.name, email: dept.email, pincode: dept.pincode, role: "ROLE_DEPARTMENT", status: dept.status, regDate: dept.regDate })}
                                    >
                                      Details
                                    </button>
                                    <button
                                      className="admin-btn admin-btn-sm admin-btn-secondary"
                                      onClick={() => { setActiveTab("broadcast"); setSearchQuery(dept.name); }}
                                    >
                                      Posts
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            ))
                        )}
                      </tbody>
                    </table>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: ALL COMMUNITIES ──────────── */}
            {activeTab === "communities" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">All Platform Communities</div>
                    <div className="admin-section-sub">Database count: {communitiesDb.length} groups</div>
                  </div>
                  <button
                    className="admin-btn admin-btn-secondary admin-btn-sm flex items-center gap-1.5"
                    onClick={fetchLiveCommunities}
                    disabled={fetchingCommunities}
                  >
                    <RefreshCw size={14} className={fetchingCommunities ? "animate-spin" : ""} />
                    Refresh Communities
                  </button>
                </div>

                {/* Stats cards row */}
                <div className="grid grid-cols-3 gap-4 mb-6">
                  <div className="admin-mini-stat bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-4 text-center">
                    <div className="admin-mini-val text-[var(--accent2)] font-black text-2xl">
                      {communityStats?.totalCommunities ?? communitiesDb.length}
                    </div>
                    <div className="admin-mini-label text-[10px] uppercase font-mono tracking-wider opacity-60 mt-1">Total Communities</div>
                  </div>
                  <div className="admin-mini-stat bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-4 text-center">
                    <div className="admin-mini-val text-[var(--success)] font-black text-2xl">
                      {communityStats?.activeCommunities ?? communitiesDb.filter(c => c.status === "Active").length}
                    </div>
                    <div className="admin-mini-label text-[10px] uppercase font-mono tracking-wider opacity-60 mt-1">Active</div>
                  </div>
                  <div className="admin-mini-stat bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-4 text-center">
                    <div className="admin-mini-val text-[var(--warn)] font-black text-2xl">
                      {communityStats?.archivedCommunities ?? communitiesDb.filter(c => c.status !== "Active").length}
                    </div>
                    <div className="admin-mini-label text-[10px] uppercase font-mono tracking-wider opacity-60 mt-1">Archived / Suspended</div>
                  </div>
                </div>

                <div className="admin-card">
                  <div className="admin-card-head">
                    <div className="admin-card-title">Community Catalog</div>
                  </div>
                  <div className="admin-card-body p-0">
                    <div className="admin-table-scroll">
                    <table className="admin-data-table">
                      <thead>
                        <tr>
                          <th>COMMUNITY</th>
                          <th>MEMBERS</th>
                          <th>HEALTH SCORE</th>
                          <th>TIER</th>
                          <th>FEED REACH</th>
                          <th>ACTIONS</th>
                        </tr>
                      </thead>
                      <tbody>
                        {fetchingCommunities && (
                          <tr>
                            <td colSpan={6} className="text-center py-4 text-sm text-[var(--text-secondary)]">
                              <div className="flex items-center justify-center gap-2">
                                <RefreshCw className="animate-spin text-[var(--accent)]" size={14} />
                                <span>Syncing with communities database...</span>
                              </div>
                            </td>
                          </tr>
                        )}
                        {(() => {
                          const filteredComms = [...communitiesDb]
                            .sort((a, b) => (b.healthScore || 0) - (a.healthScore || 0))
                            .filter(c =>
                              c.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                              c.slug.toLowerCase().includes(searchQuery.toLowerCase()) ||
                              (c.category && c.category.toLowerCase().includes(searchQuery.toLowerCase()))
                            );

                          if (filteredComms.length === 0) {
                            return (
                              <tr>
                                <td colSpan={6} className="text-center py-8 text-sm text-[var(--text-muted)] italic">
                                  No communities found matching filters.
                                </td>
                              </tr>
                            );
                          }

                          return filteredComms.map(comm => {
                            // Derived feed reach scope from real locationName / wardName
                            let feedReach = "National";
                            if (comm.privacy !== "PUBLIC" || !comm.feedEligible) {
                              feedReach = "None (Private)";
                            } else if (comm.wardName) {
                              feedReach = `Ward (${comm.wardName})`;
                            } else if (comm.locationName) {
                              feedReach = `Local (${comm.locationName})`;
                            }

                            // Match status of active/archived to health score updates
                            const healthVal = comm.healthScore ?? 0.0;

                            return (
                              <tr key={comm.id} className="user-row">
                                <td>
                                  <div className="flex items-center gap-2">
                                    <div className="admin-u-avatar admin-ua-purple">
                                      {decodeHTML(comm.name).charAt(0).toUpperCase()}
                                    </div>
                                    <div>
                                      <div className="admin-td-name">{decodeHTML(comm.name)}</div>
                                      <div className="text-[10px] text-[var(--text-muted)] truncate max-w-[180px]" title={decodeHTML(comm.description)}>{decodeHTML(comm.description)}</div>
                                    </div>
                                  </div>
                                </td>
                                <td>{comm.memberCount ?? 0}</td>
                                <td>
                                  <div className="flex items-center gap-2">
                                    <span className="font-mono text-xs font-bold">{healthVal.toFixed(0)}%</span>
                                    <div className="w-16 h-2 bg-base-300 rounded-full overflow-hidden">
                                      <div 
                                        className="h-full rounded-full" 
                                        style={{ 
                                          width: `${healthVal}%`,
                                          background: healthVal >= 75 ? "var(--success)" : healthVal >= 50 ? "var(--accent2)" : "var(--warn)"
                                        }}
                                      />
                                    </div>
                                  </div>
                                </td>
                                <td>
                                  <span className="font-mono text-xs font-bold">
                                    {comm.healthTierEmoji || "🔔"} {comm.healthTier || "QUIET"}
                                  </span>
                                </td>
                                <td>
                                  <span className="admin-pincode-tag">{feedReach}</span>
                                </td>
                                <td>
                                  <button
                                    className={`admin-btn admin-btn-sm ${comm.status === "Active" ? "admin-btn-danger" : "admin-btn-primary"}`}
                                    onClick={() => toggleCommunityStatus(comm.id)}
                                  >
                                    {comm.status === "Active" ? "Archive" : "Activate"}
                                  </button>
                                </td>
                              </tr>
                            );
                          });
                        })()}
                      </tbody>
                    </table>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: CONTENT MONITOR ──────────── */}
            {activeTab === "content" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                {/* Section Header */}
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">Compliance & Content Moderation</div>
                    <div className="admin-section-sub">
                      Investigate citizen reports and enforce IT Rules 2021 & BNS guidelines
                    </div>
                  </div>
                  <button 
                    className="admin-btn admin-btn-secondary admin-btn-sm flex items-center gap-1.5" 
                    onClick={fetchModerationData}
                    disabled={loadingModeration}
                  >
                    <RefreshCw className={loadingModeration ? "animate-spin" : ""} size={13} />
                    Sync Queues
                  </button>
                </div>

                {/* Stats Cards */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="admin-card p-4 flex flex-col justify-between h-24 bg-amber-500/5 border-amber-500/10">
                    <span className="text-[10px] uppercase font-black tracking-wider opacity-60">Bad Words Loaded</span>
                    <span className="text-2xl font-black text-amber-500">{overviewStats?.badWordsLoaded ?? 0}</span>
                  </div>
                  <div className="admin-card p-4 flex flex-col justify-between h-24 bg-rose-500/5 border-rose-500/15 relative overflow-hidden group">
                    <div className="absolute top-3 right-3 flex items-center justify-center">
                      <span className="relative flex h-2 w-2">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75"></span>
                        <span className="relative inline-flex rounded-full h-2 w-2 bg-rose-500"></span>
                      </span>
                    </div>
                    <span className="text-[10px] uppercase font-black tracking-wider text-rose-500">Pending Reports</span>
                    <span className="text-2xl font-black text-rose-500">{moderationStats.totalPending}</span>
                  </div>
                </div>

                {/* Two-Column Grid */}
                <div className="admin-two-col">
                  {/* Left Column: Recent Flagged Content */}
                  <div className="admin-card">
                    <div className="admin-card-head">
                      <div>
                        <div className="admin-card-title">Recent Flagged Content</div>
                      </div>
                    </div>
                    <div className="admin-card-body p-0">
                      <div className="admin-table-scroll">
                        <table className="admin-data-table">
                          <thead>
                            <tr>
                              <th>POST ID</th>
                              <th>USER</th>
                              <th>BAD WORD</th>
                              <th>TIME</th>
                              <th>ACTION</th>
                            </tr>
                          </thead>
                          <tbody>
                            {loadingModeration ? (
                              <tr>
                                <td colSpan={5} className="text-center py-8 text-sm text-[var(--text-secondary)]">
                                  <div className="flex items-center justify-center gap-2">
                                    <RefreshCw className="animate-spin text-[var(--accent)]" size={14} />
                                    <span>Syncing moderation list...</span>
                                  </div>
                                </td>
                              </tr>
                            ) : historyReports.length === 0 ? (
                              <tr>
                                <td colSpan={5} className="text-center py-8 text-sm text-[var(--text-muted)] italic">
                                  No flagged content reports found.
                                </td>
                              </tr>
                            ) : (
                              historyReports.map((report) => (
                                <tr key={report.id}>
                                  <td style={{ fontFamily: "var(--font-mono)", fontWeight: "bold" }}>
                                    #R-{report.id}
                                  </td>
                                  <td>@{report.reporter?.username || "anonymous"}</td>
                                  <td>
                                    <span className={`admin-badge ${report.isEmergency ? "admin-badge-inactive" : "admin-badge-pending"}`}>
                                      {report.category || "SPAM"}
                                    </span>
                                  </td>
                                  <td style={{ fontFamily: "var(--font-mono)", fontSize: "12px" }}>
                                    {formatRegDate(report.createdAt)}
                                  </td>
                                  <td>
                                    {report.status === "PENDING" ? (
                                      <button 
                                        className="admin-btn admin-btn-danger admin-btn-sm"
                                        onClick={() => handleOpenResolveDialog(report.id)}
                                      >
                                        Review
                                      </button>
                                    ) : (
                                      <span className={`admin-badge ${
                                        report.status === "RESOLVED_REMOVED"
                                          ? "admin-badge-inactive"
                                          : "admin-badge-active"
                                      }`}>
                                        {report.status === "RESOLVED_REMOVED" ? "REMOVED" : "DISMISSED"}
                                      </span>
                                    )}
                                  </td>
                                </tr>
                              ))
                            )}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </div>

                  {/* Right Column: Bad Word Filter Control */}
                  <div className="admin-card">
                    <div className="admin-card-head">
                      <div>
                        <div className="admin-card-title">Filter Engine Control</div>
                        <div className="admin-card-subtitle">Profanity and Hate Speech Filter Rules</div>
                      </div>
                    </div>
                    <div className="admin-card-body">
                      <div className="space-y-4">
                        <div className="flex justify-between items-center bg-black/10 border border-[var(--border)] rounded-xl p-3">
                          <div>
                            <div className="text-[10px] uppercase font-mono tracking-wider text-[var(--text-muted)]">Active Profanity Dictionary</div>
                            <div className="text-xl font-black text-amber-500 mt-1">{overviewStats?.badWordsLoaded ?? 0} words</div>
                          </div>
                          <button 
                            className="admin-btn admin-btn-primary admin-btn-sm"
                            onClick={handleReloadBadWords}
                            disabled={executingAction === "bad-words"}
                          >
                            {executingAction === "bad-words" ? "Reloading..." : "Hot-Reload"}
                          </button>
                        </div>

                        <div className="text-xs text-[var(--text-secondary)] space-y-2">
                          <div className="font-bold uppercase tracking-wider text-[10px] text-[var(--text-muted)] mt-2">Active IT Guidelines & Rules:</div>
                          <ul className="list-disc pl-4 space-y-1">
                            <li>IT Rules 2021 Rule 3(1)(b) compliance engine active.</li>
                            <li>Hate speech, obscenity, and harassment auto-flag thresholds enabled.</li>
                            <li>Emergency report SLA review set at 24 hours.</li>
                            <li>Standard report SLA review set at 15 days.</li>
                          </ul>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: CHAT STATISTICS ──────────── */}
            {activeTab === "chat" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">Support Chat Analytics</div>
                    <div className="admin-section-sub">Dynamic counts matching chatSocket connection metrics</div>
                  </div>
                </div>

                {/* Top stats cards as a two-column grid */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
                  {/* Left stats card: Total Daily Active Users (onlineCount) */}
                  <div className="admin-card p-6 flex flex-col justify-between">
                    <div>
                      <div className="text-[10px] uppercase font-mono tracking-wider text-[var(--text-muted)]">Total Daily Active Users</div>
                      <div className="text-3xl font-black text-blue-500 mt-2">{onlineCount} Citizens</div>
                      <div className="text-xs text-[var(--text-secondary)] mt-1">Live active chat websocket sockets</div>
                    </div>
                    <div className="mt-4">
                      <div className="flex justify-between text-[10px] font-mono text-[var(--text-muted)]">
                        <span>CAPACITY TARGET</span>
                        <span>{(((onlineCount ?? 0) / 500) * 100).toFixed(1)}% ({(onlineCount ?? 0)}/500)</span>
                      </div>
                      <div className="admin-progress-bar">
                        <div className="admin-progress-fill" style={{ width: `${Math.min(100, ((onlineCount ?? 0) / 500) * 100)}%`, background: "var(--accent2)" }}></div>
                      </div>
                    </div>
                  </div>

                  {/* Right stats card: Citizens, Departments, Admins from overviewStats */}
                  <div className="admin-card p-6">
                    <div className="text-[10px] uppercase font-mono tracking-wider text-[var(--text-muted)] mb-4">Platform User Distribution</div>
                    <div className="grid grid-cols-3 gap-2">
                      <div className="text-center p-3 bg-black/10 border border-[var(--border)] rounded-xl">
                        <div className="text-lg font-black text-emerald-500">{overviewStats?.totalCitizens ?? 0}</div>
                        <div className="text-[9px] uppercase font-mono tracking-wider text-[var(--text-muted)] mt-1">Citizens</div>
                      </div>
                      <div className="text-center p-3 bg-black/10 border border-[var(--border)] rounded-xl">
                        <div className="text-lg font-black text-blue-500">{overviewStats?.totalDepartments ?? 0}</div>
                        <div className="text-[9px] uppercase font-mono tracking-wider text-[var(--text-muted)] mt-1">Depts</div>
                      </div>
                      <div className="text-center p-3 bg-black/10 border border-[var(--border)] rounded-xl">
                        <div className="text-lg font-black text-orange-500">{overviewStats?.totalAdmins ?? 0}</div>
                        <div className="text-[9px] uppercase font-mono tracking-wider text-[var(--text-muted)] mt-1">Admins</div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Middle row stats: Active Sessions, Queue Size */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
                  <div className="admin-card p-4 flex flex-col justify-between h-24">
                    <span className="text-[10px] uppercase font-black tracking-wider opacity-60">Active Sessions</span>
                    <span className="text-2xl font-black text-blue-400">{liveSessions}</span>
                  </div>
                  <div className="admin-card p-4 flex flex-col justify-between h-24">
                    <span className="text-[10px] uppercase font-black tracking-wider opacity-60">Queue Size</span>
                    <span className="text-2xl font-black text-amber-500">{overviewStats?.chatQueueSize ?? 0}</span>
                  </div>
                </div>
              </motion.div>
            )}
            {/* ──────────── VIEW: BROADCASTS ──────────── */}
            {activeTab === "broadcast" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                  <div className="admin-section-header">
                    <div>
                      <div className="admin-section-title">Broadcast Management</div>
                    </div>
                  </div>

                {/* Scope stat cards derived from loaded list */}
                <div className="admin-mini-stat-row" style={{ gridTemplateColumns: "repeat(4, 1fr)" }}>
                  {[
                    { label: "Country Wide", scope: "COUNTRY", color: "var(--accent)" },
                    { label: "State Level", scope: "STATE", color: "var(--accent2)" },
                    { label: "District Level", scope: "DISTRICT", color: "var(--success)" },
                    { label: "Area (Pincode)", scope: "AREA", color: "var(--warn)" },
                  ].map(({ label, scope, color }) => (
                    <div
                      key={scope}
                      className="admin-mini-stat cursor-pointer hover:opacity-80 transition-opacity"
                      onClick={() => setBroadcastSearch(scope.toLowerCase())}
                      title={`Filter by ${label}`}
                    >
                      <div className="admin-mini-val" style={{ color }}>
                        {broadcastStats !== null ? (broadcastStats["broadcasts" + scope] ?? 0).toLocaleString() : "..."}
                      </div>
                      <div className="admin-mini-label">{label}</div>
                    </div>
                  ))}
                </div>

                <div className="admin-card">
                  <div className="admin-card-head">
                    <div className="admin-card-title">All Broadcasts {broadcastsList.length > 0 && <span style={{ fontSize: "12px", color: "var(--text-secondary)", fontWeight: 400 }}>({broadcastsList.length} loaded)</span>}</div>
                  </div>
                  <div className="admin-card-body p-0">
                    <div className="admin-table-scroll">
                      <table className="admin-data-table">
                        <thead>
                          <tr>
                            <th>ID</th>
                            <th>DEPT / USER</th>
                            <th>SCOPE</th>
                            <th>TARGET</th>
                            <th>POSTED</th>
                            <th>RESOLVED</th>
                            <th>ACTION</th>
                          </tr>
                        </thead>
                        <tbody>
                          {loadingBroadcasts ? (
                            <tr>
                              <td colSpan={7} className="text-center py-8 text-sm text-[var(--text-secondary)]">
                                <div className="flex items-center justify-center gap-2">
                                  <RefreshCw className="animate-spin text-[var(--accent)]" size={14} />
                                  <span>Loading broadcasts...</span>
                                </div>
                              </td>
                            </tr>
                          ) : (() => {
                              const activeSearch = (broadcastSearch || searchQuery).trim().toLowerCase();
                              const filtered = activeSearch
                                ? broadcastsList.filter(b =>
                                    b.username?.toLowerCase().includes(activeSearch) ||
                                    b.scope?.toLowerCase().includes(activeSearch) ||
                                    b.target?.toLowerCase().includes(activeSearch)
                                  )
                                : broadcastsList;
                              return filtered.length === 0 ? (
                            <tr>
                              <td colSpan={7} className="text-center py-8 text-sm text-[var(--text-muted)] italic">
                                {activeSearch ? `No broadcasts matching "${activeSearch}".` : "No broadcasts found."}
                              </td>
                            </tr>
                              ) : filtered.map(b => (
                              <tr key={b.id}>
                                <td style={{ fontFamily: "var(--font-mono)" }}>#B-{b.id}</td>
                                <td className="admin-td-name">
                                   {(() => {
                                     const userObj = usersDb.find(u => u.username === b.username);
                                     const role = userObj?.role || (b.isGovernmentBroadcast ? "ROLE_DEPARTMENT" : "ROLE_USER");
                                     if (role === "ROLE_ADMIN") {
                                       return (
                                         <div className="flex items-center gap-1.5" title="Administrator">
                                           <Shield size={13} className="text-[#fb923c]" />
                                           <span className="text-[#fb923c] font-medium">{b.username}</span>
                                           <span className="text-[10px] px-1.5 py-0.5 rounded bg-orange-500/10 text-orange-400 border border-orange-500/20 font-mono scale-90 origin-left">ADMIN</span>
                                         </div>
                                       );
                                     } else if (role === "ROLE_DEPARTMENT") {
                                       return (
                                         <div className="flex items-center gap-1.5" title="Department User">
                                           <Building size={13} className="text-[#60a5fa]" />
                                           <span className="text-[#60a5fa] font-medium">{b.username}</span>
                                           <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 font-mono scale-90 origin-left">DEPT</span>
                                         </div>
                                       );
                                     } else {
                                       return (
                                         <div className="flex items-center gap-1.5" title="Citizen User">
                                           <User size={13} className="text-gray-400" />
                                           <span className="text-gray-400 font-normal">{b.username}</span>
                                           <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-500/10 text-gray-400 border border-gray-500/20 font-mono scale-90 origin-left">CITIZEN</span>
                                         </div>
                                       );
                                     }
                                   })()}
                                 </td>
                                <td>
                                  <span className={`admin-badge ${
                                    b.scope === "COUNTRY" ? "admin-badge-pending" :
                                    b.scope === "STATE" ? "admin-badge-admin" : "admin-badge-dept"
                                  }`}>
                                    {b.scope}
                                  </span>
                                </td>
                                <td><span className="admin-pincode-tag">{b.target}</span></td>
                                <td style={{ fontFamily: "var(--font-mono)", fontSize: "12px" }}>{b.posted}</td>
                                <td>
                                  <span className={`admin-badge ${b.resolved === "Yes" ? "admin-badge-active" : "admin-badge-inactive"}`}>
                                    {b.resolved}
                                  </span>
                                </td>
                                <td>
                                  <button
                                    className="admin-btn admin-btn-sm"
                                    style={{ background: "rgba(239,68,68,0.1)", color: "#ef4444", border: "1px solid rgba(239,68,68,0.3)", padding: "3px 10px" }}
                                    onClick={async () => {
                                      if (!window.confirm(`Delete broadcast #${b.id}?`)) return;
                                      try {
                                        await axiosInstance.delete(`/api/posts/${b.id}`);
                                        setBroadcastsList(prev => prev.filter(x => x.id !== b.id));
                                        fetchStats();
                                      } catch { alert("Failed to delete broadcast."); }
                                    }}
                                  >
                                    Delete
                                  </button>
                                </td>
                              </tr>
                            ));
                          })()}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: COPYRIGHT CLAIMS ──────────── */}
            {activeTab === "copyright" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">Copyright Infringement Claims</div>
                    <div className="admin-section-sub">Acknowledge external legal notices within 24-hour SLA</div>
                  </div>
                  <button className="admin-btn admin-btn-secondary admin-btn-sm" onClick={fetchPendingClaims} disabled={loadingClaims}>
                    <RefreshCw size={13} className={loadingClaims ? "animate-spin mr-1" : "mr-1"} /> Refresh Claims
                  </button>
                </div>

                <div className="admin-card">
                  <div className="admin-card-body">
                    {loadingClaims ? (
                      <div className="flex flex-col items-center justify-center py-12 gap-3">
                        <Loader2 className="w-8 h-8 text-blue-600 animate-spin" />
                        <span className="text-xs opacity-50 font-mono">Loading pending claims...</span>
                      </div>
                    ) : claimsList.length === 0 ? (
                      <div className="text-center py-12 text-slate-500 font-mono text-sm">
                        No pending copyright claims found.
                      </div>
                    ) : (
                      <>
                        <div className="admin-table-wrapper">
                          <table className="admin-table">
                            <thead>
                              <tr>
                                <th>REFERENCE ID</th>
                                <th>CLAIMANT / COMPANY</th>
                                <th>CONTENT TYPE</th>
                                <th>URLS</th>
                                <th>SUBMITTED AT</th>
                                <th>ACTION</th>
                              </tr>
                            </thead>
                            <tbody>
                              {claimsList.map((claim) => (
                                <tr key={claim.id}>
                                  <td className="font-mono font-bold text-blue-500">{claim.referenceId}</td>
                                  <td>
                                    <div>{claim.claimantName}</div>
                                    <div className="text-xs opacity-50">{claim.claimantCompany || "Personal Claim"}</div>
                                  </td>
                                  <td>
                                    <span className="px-2 py-0.5 rounded bg-blue-500/10 text-blue-500 text-xs font-bold">
                                      {claim.originalWorkType}
                                    </span>
                                  </td>
                                  <td className="font-mono text-xs">{claim.infringingUrls?.length || 0} link(s)</td>
                                  <td>{new Date(claim.createdAt).toLocaleDateString()}</td>
                                  <td>
                                    <button
                                      className="admin-btn admin-btn-primary admin-btn-sm"
                                      onClick={() => setSelectedClaimForReview(claim)}
                                    >
                                      Review
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>

                        {/* Pagination */}
                        {claimsTotalPages > 1 && (
                          <div className="flex justify-between items-center mt-6">
                            <button
                              className="admin-btn admin-btn-secondary admin-btn-sm"
                              onClick={() => setClaimsPage((p) => Math.max(1, p - 1))}
                              disabled={claimsPage === 1}
                            >
                              Previous
                            </button>
                            <span className="text-xs font-mono opacity-60">
                              Page {claimsPage} of {claimsTotalPages}
                            </span>
                            <button
                              className="admin-btn admin-btn-secondary admin-btn-sm"
                              onClick={() => setClaimsPage((p) => Math.min(claimsTotalPages, p + 1))}
                              disabled={claimsPage === claimsTotalPages}
                            >
                              Next
                            </button>
                          </div>
                        )}
                      </>
                    )}
                  </div>
                </div>
              </motion.div>
            )}

            {/* ──────────── VIEW: SYSTEM HEALTH ──────────── */}
            {activeTab === "system" && (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.15 }}
                className="space-y-6"
              >
                <div className="admin-section-header">
                  <div>
                    <div className="admin-section-title">System Health & Operations</div>
                    <div className="admin-section-sub">Scheduler status · Storage · Notification cleanup</div>
                  </div>
                  <button className="admin-btn admin-btn-secondary admin-btn-sm" onClick={fetchSystemHealth} disabled={loadingHealth}>
                    <RefreshCw size={13} className={loadingHealth ? "animate-spin mr-1" : "mr-1"} /> Refresh Health
                  </button>
                </div>

                <div className="admin-mini-stat-row" style={{ gridTemplateColumns: "repeat(3, 1fr)" }}>
                  <div className="admin-mini-stat">
                    <div className="admin-mini-val" style={{ color: "var(--accent2)" }}>
                      {apiLatency !== null ? `${apiLatency}ms` : "..."}
                    </div>
                    <div className="admin-mini-label">API Latency</div>
                  </div>
                  <div className="admin-mini-stat">
                    <div className="admin-mini-val" style={{ color: "var(--warn)" }}>
                      {overviewStats?.badWordsLoaded ?? 0}
                    </div>
                    <div className="admin-mini-label">Bad Words</div>
                  </div>
                  <div className="admin-mini-stat">
                    <div className="admin-mini-val" style={{ color: "var(--success)" }}>
                      {healthData?.storage || "Cloudinary"}
                    </div>
                    <div className="admin-mini-label">Storage</div>
                  </div>
                </div>

                <div className="admin-two-col">
                  {/* Service Status */}
                  <div className="admin-card">
                    <div className="admin-card-head">
                      <div className="admin-card-title">Service Status</div>
                      <div className="admin-card-subtitle">Platform microservices connectivity</div>
                    </div>
                    <div className="admin-card-body">
                      <div className="admin-qstat">
                        {loadingHealth ? (
                          <div className="text-center py-6 text-xs text-[var(--text-secondary)]">
                            Checking services...
                          </div>
                        ) : healthData?.services ? (
                          healthData.services.map((srv: any, idx: number) => (
                            <div className="admin-qstat-row" key={idx}>
                              <div className="admin-qstat-label">{srv.name}</div>
                              <span className={`admin-badge ${srv.status === "HEALTHY" ? "admin-badge-active" : "admin-badge-inactive"}`}>
                                {srv.status}
                              </span>
                            </div>
                          ))
                        ) : (
                          <div className="text-center py-6 text-xs text-rose-500/80 font-bold italic">
                            Failed to retrieve service status from server.
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Quick Actions */}
                  <div className="admin-card">
                    <div className="admin-card-head">
                      <div className="admin-card-title">Quick Actions</div>
                      <div className="admin-card-subtitle">Execute administrative actions</div>
                    </div>
                    <div className="admin-card-body">
                      <div className="admin-btn-actions" style={{ flexDirection: "column", gap: "10px", alignItems: "stretch" }}>
                        <button
                          className="admin-btn admin-btn-secondary"
                          style={{ width: "100%" }}
                          onClick={handleRunCleanup}
                          disabled={executingAction !== null}
                        >
                          {executingAction === "cleanup" ? "Processing..." : "Run File Cleanup Queue"}
                        </button>
                        <button
                          className="admin-btn admin-btn-secondary"
                          style={{ width: "100%" }}
                          onClick={handleResetCounters}
                          disabled={executingAction !== null}
                        >
                          {executingAction === "reset" ? "Processing..." : "Reset Weekly Community Counters"}
                        </button>
                        <button
                          className="admin-btn admin-btn-secondary"
                          style={{ width: "100%" }}
                          onClick={handleTriggerNotifCleanup}
                          disabled={executingAction !== null}
                        >
                          {executingAction === "notif-cleanup" ? "Processing..." : "Trigger Notification Cleanup (30d)"}
                        </button>
                        <button
                          className="admin-btn admin-btn-secondary"
                          style={{ width: "100%" }}
                          onClick={handleReloadBadWords}
                          disabled={executingAction !== null}
                        >
                          {executingAction === "bad-words" ? "Processing..." : "Reload Bad Word Filter"}
                        </button>
                        <button
                          className="admin-btn admin-btn-danger"
                          style={{ width: "100%", marginTop: "8px" }}
                          onClick={handleForceEndAllChat}
                          disabled={executingAction !== null}
                        >
                          {executingAction === "chat-kill" ? "Processing..." : "Force-end All Chat Sessions"}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

          </AnimatePresence>
        </div>
      </div>

      {/* Approval Modal (for department onboarding queue) */}
      <AnimatePresence>
        {showApproveModal && (
          <div className="fixed inset-0 z-[110] flex items-center justify-center p-4 backdrop-blur-md bg-black/60">
            <motion.div
              initial={{ opacity: 0, scale: 0.9, y: 30 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.9, y: 30 }}
              className="w-full max-w-md overflow-hidden rounded-[24px] border border-[var(--border)] bg-[var(--bg-surface)] shadow-2xl p-8 space-y-6"
            >
              <div className="space-y-1.5 flex flex-col items-center text-center">
                <div className="w-16 h-16 rounded-2xl bg-emerald-500/10 flex items-center justify-center text-emerald-500 mb-2">
                  <ShieldCheck size={36} />
                </div>
                <h3 className="text-xl font-bold text-[var(--text-primary)]">Approve Department</h3>
                <p className="text-sm text-[var(--text-secondary)]">Provision credentials for {showApproveModal.deptName}</p>
              </div>

              <div className="space-y-4">
                <div className="admin-form-group">
                  <label className="admin-form-label">Auto-Filled Login Email</label>
                  <input
                    type="email"
                    className="admin-form-input bg-black/20"
                    value={approvedForm.email}
                    onChange={e => setApprovedForm({ ...approvedForm, email: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-form-label">Temporary Access Password</label>
                  <input
                    type="text"
                    className="admin-form-input bg-black/20"
                    value={approvedForm.password}
                    onChange={e => setApprovedForm({ ...approvedForm, password: e.target.value })}
                  />
                </div>

                <div className="admin-form-group">
                  <label className="admin-form-label">Assigned GID (ID)</label>
                  <input
                    type="text"
                    className="admin-form-input bg-black/20"
                    value={approvedForm.identity}
                    readOnly
                  />
                </div>
              </div>

              <div className="flex gap-3">
                <button
                  onClick={() => setShowApproveModal(null)}
                  className="admin-btn admin-btn-secondary flex-1"
                >
                  Cancel
                </button>
                <button
                  onClick={confirmApproveRequest}
                  className="admin-btn admin-btn-primary flex-1"
                >
                  Generate & Onboard
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Report Resolution Modal */}
      <AnimatePresence>
        {resolvingReportId && (
          <div className="fixed inset-0 z-[110] flex items-center justify-center p-4 backdrop-blur-md bg-black/60">
            <motion.div
              initial={{ opacity: 0, scale: 0.9, y: 30 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.9, y: 30 }}
              className="w-full max-w-md overflow-hidden rounded-[24px] border border-[var(--border)] bg-[var(--bg-surface)] shadow-2xl p-8 space-y-6"
            >
              <div className="space-y-1.5 flex flex-col items-center text-center">
                <div className="w-16 h-16 rounded-2xl bg-red-500/10 flex items-center justify-center text-red-500 mb-2">
                  <ShieldAlert size={36} />
                </div>
                <h3 className="text-xl font-bold text-[var(--text-primary)]">Resolve Content Report</h3>
                <p className="text-sm text-[var(--text-secondary)]">Action report #{resolvingReportId} according to IT Guidelines</p>
              </div>

              <div className="space-y-4">
                <div className="admin-form-group">
                  <label className="admin-form-label">Resolution Action</label>
                  <div className="grid grid-cols-2 gap-2 mt-1">
                    <button
                      type="button"
                      onClick={() => setResolveForm({ ...resolveForm, resolution: "RESOLVED_REMOVED" })}
                      className={`px-3 py-2.5 rounded-xl border text-xs font-bold transition-all ${
                        resolveForm.resolution === "RESOLVED_REMOVED"
                          ? "bg-red-500/10 border-red-500/30 text-red-500 shadow-sm"
                          : "border-transparent bg-black/20 text-[var(--text-secondary)] hover:bg-black/35"
                      }`}
                    >
                      Remove Content
                    </button>
                    <button
                      type="button"
                      onClick={() => setResolveForm({ ...resolveForm, resolution: "RESOLVED_DISMISSED" })}
                      className={`px-3 py-2.5 rounded-xl border text-xs font-bold transition-all ${
                        resolveForm.resolution === "RESOLVED_DISMISSED"
                          ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-500 shadow-sm"
                          : "border-transparent bg-black/20 text-[var(--text-secondary)] hover:bg-black/35"
                      }`}
                    >
                      Dismiss Report
                    </button>
                  </div>
                </div>

                <div className="admin-form-group">
                  <label className="admin-form-label">Compliance Notes / Rationale</label>
                  <textarea
                    rows={4}
                    placeholder="Provide legal rationale or investigation notes (e.g. Violates IT Rules 2021 Rule 3(1)(b) due to obscene content...)"
                    className="admin-form-input bg-black/20 text-xs py-2 h-24 resize-none font-medium"
                    value={resolveForm.notes}
                    onChange={e => setResolveForm({ ...resolveForm, notes: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="flex gap-3">
                <button
                  onClick={() => setResolvingReportId(null)}
                  className="admin-btn admin-btn-secondary flex-1"
                  disabled={submittingResolution}
                >
                  Cancel
                </button>
                <button
                  onClick={submitResolveReport}
                  className="admin-btn admin-btn-primary flex-1"
                  disabled={submittingResolution}
                >
                  {submittingResolution ? "Submitting..." : "Apply Resolution"}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Detail Slide-over Panel (new right sidebar when clicked) */}
      <div
        className={`admin-posts-panel-backdrop ${selectedUserForDetails ? "open" : ""}`}
        onClick={() => setSelectedUserForDetails(null)}
      />
      <div className={`admin-posts-panel ${selectedUserForDetails ? "open" : ""}`}>
        {selectedUserForDetails && (
          <>
            <div className="admin-posts-panel-head">
              <div className="admin-posts-panel-title-row">
                <div className="admin-posts-panel-user">
                  <div className="admin-posts-panel-avatar">
                    {(selectedUserForDetails.username || selectedUserForDetails.name || "U").charAt(0).toUpperCase()}
                  </div>
                  <div className="admin-posts-panel-meta">
                    <div className="admin-posts-panel-name text-[var(--text-primary)]">
                      {selectedUserForDetails.username || selectedUserForDetails.name}
                    </div>
                    <div className="admin-posts-panel-sub">
                      {selectedUserForDetails.role || "ROLE_USER"} · Pincode: {selectedUserForDetails.pincode || "N/A"}
                    </div>
                  </div>
                </div>
                <button
                  className="modal-close"
                  onClick={() => setSelectedUserForDetails(null)}
                >
                  ✕
                </button>
              </div>

              {/* Statistics Row inside details sidebar */}
              <div className="admin-posts-panel-stats mt-2" style={{ gridTemplateColumns: "repeat(2, 1fr)" }}>
                <div className="admin-posts-panel-stat">
                  <div className="admin-posts-panel-stat-val text-[var(--accent)]">
                    {userContributionsCount !== null ? userContributionsCount : "..."}
                  </div>
                  <div className="admin-posts-panel-stat-lbl">Contributions</div>
                </div>
                <div className="admin-posts-panel-stat">
                  <div className={`admin-posts-panel-stat-val ${selectedUserForDetails.status === "Active" ? "text-[var(--success)]" : "text-[var(--danger)]"}`}>
                    {selectedUserForDetails.status || "Active"}
                  </div>
                  <div className="admin-posts-panel-stat-lbl">Account Status</div>
                </div>
              </div>
            </div>

            {/* Filter chips inside details sidebar (only for departments and admins) */}
            {selectedUserForDetails.role !== "ROLE_USER" && (
              <div className="admin-posts-panel-filter mt-3">
                <span
                  className={`admin-filter-chip ${panelFilter === "all" ? "active" : ""}`}
                  onClick={() => setPanelFilter("all")}
                >
                  All Actions
                </span>
                <span
                  className={`admin-filter-chip ${panelFilter === "broadcast" ? "active" : ""}`}
                  onClick={() => setPanelFilter("broadcast")}
                >
                  Broadcasts
                </span>
                <span
                  className={`admin-filter-chip ${panelFilter === "resolved" ? "active" : ""}`}
                  onClick={() => setPanelFilter("resolved")}
                >
                  Resolutions
                </span>
              </div>
            )}

            {/* Body */}
            <div className="admin-posts-panel-body">
              {panelLoading ? (
                <div className="admin-posts-panel-loading space-y-4">
                  {[1, 2, 3].map(i => (
                    <div key={i} className="admin-post-card space-y-3">
                      <div className="h-2.5 w-24 admin-skeleton" />
                      <div className="h-4 w-full admin-skeleton" />
                      <div className="h-3.5 w-40 admin-skeleton" />
                    </div>
                  ))}
                </div>
              ) : panelPosts.length === 0 ? (
                <div className="admin-posts-panel-empty">No logged activities for this account.</div>
              ) : (
                panelPosts
                  .filter(post => panelFilter === "all" || post.type === panelFilter)
                  .map(post => (
                    <div key={post.id} className="admin-post-card">
                      <div className="admin-post-card-head">
                        <span className={`admin-post-type-tag ${post.type}`}>
                          {post.type}
                        </span>
                        <span className="text-[11px] font-mono text-[var(--text-muted)]">{post.date}</span>
                      </div>
                      <p className="admin-post-card-body">
                        {post.content}
                      </p>
                      <div className="admin-post-card-foot">
                        <span><Heart size={12} className="text-[#f97316]" /> {post.likes}</span>
                        <span><MessageSquare size={12} className="text-[#3b82f6]" /> {post.comments}</span>
                      </div>
                    </div>
                  ))
              )}
            </div>
          </>
        )}
      </div>

      {/* ── COPYRIGHT CLAIM REVIEW MODAL ── */}
      <AnimatePresence>
        {selectedClaimForReview && (
          <div className="fixed inset-0 z-[3000] flex items-center justify-center p-4">
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="absolute inset-0 bg-black/60 backdrop-blur-sm"
              onClick={() => setSelectedClaimForReview(null)}
            />

            {/* Modal Box */}
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="bg-base-200 border border-slate-700 w-full max-w-2xl rounded-3xl overflow-hidden shadow-2xl relative z-10 flex flex-col max-h-[85vh] text-left"
            >
              {/* Head */}
              <div className="p-6 border-b border-slate-800 flex justify-between items-center shrink-0">
                <div>
                  <span className="text-xs opacity-50 uppercase font-mono">Pending Legal Notice</span>
                  <h3 className="text-xl font-black text-slate-100 mt-0.5">
                    Review Claim {selectedClaimForReview.referenceId}
                  </h3>
                </div>
                <button
                  onClick={() => setSelectedClaimForReview(null)}
                  className="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 flex items-center justify-center text-slate-300 font-bold"
                >
                  ✕
                </button>
              </div>

              {/* Body */}
              <div className="p-6 overflow-y-auto space-y-6 text-sm text-slate-300">
                {/* Claimant Details */}
                <div className="space-y-2">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--accent2)]">Claimant Contact Details</h4>
                  <div className="bg-slate-900/50 border border-slate-800 p-4 rounded-2xl grid grid-cols-2 gap-y-3 gap-x-4 text-xs sm:text-sm">
                    <div>
                      <span className="opacity-50 block">Name</span>
                      <span className="font-bold text-slate-100">{selectedClaimForReview.claimantName}</span>
                    </div>
                    <div>
                      <span className="opacity-50 block">Company</span>
                      <span className="font-bold text-slate-100">{selectedClaimForReview.claimantCompany || "N/A"}</span>
                    </div>
                    <div>
                      <span className="opacity-50 block">Email</span>
                      <span className="font-semibold text-slate-100">{selectedClaimForReview.claimantEmail}</span>
                    </div>
                    <div>
                      <span className="opacity-50 block">Phone</span>
                      <span className="font-semibold text-slate-100">{selectedClaimForReview.claimantPhone || "N/A"}</span>
                    </div>
                    <div className="col-span-2">
                      <span className="opacity-50 block">Postal Address</span>
                      <span className="font-medium text-slate-200">{selectedClaimForReview.claimantAddress}</span>
                    </div>
                  </div>
                </div>

                {/* Infringing URLs */}
                <div className="space-y-2">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--accent2)]">Alleged Infringing Content URLs</h4>
                  <div className="space-y-1.5">
                    {selectedClaimForReview.infringingUrls?.map((url: string, index: number) => (
                      <a
                        key={index}
                        href={url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center justify-between p-3 rounded-xl bg-slate-900/30 border border-slate-800 hover:border-blue-500/50 text-xs text-blue-400 font-mono transition-colors"
                      >
                        <span className="truncate max-w-[90%]">{url}</span>
                        <ExternalLink size={12} className="shrink-0 ml-2" />
                      </a>
                    ))}
                  </div>
                  <div className="form-control mt-2">
                    <span className="text-xs opacity-50 block mb-1">Infringement Description</span>
                    <p className="p-4 bg-slate-900/20 border border-slate-800 rounded-2xl leading-relaxed text-xs">
                      {selectedClaimForReview.infringementDescription}
                    </p>
                  </div>
                </div>

                {/* Original Copyrighted Work */}
                <div className="space-y-2">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--accent2)]">Original Copyrighted Work</h4>
                  <div className="bg-slate-900/30 border border-slate-800 p-4 rounded-2xl space-y-3">
                    <div className="flex gap-2">
                      <span className="px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 text-xs font-bold uppercase">
                        {selectedClaimForReview.originalWorkType}
                      </span>
                      {selectedClaimForReview.originalWorkUrls?.length > 0 && (
                        <span className="text-xs opacity-50 font-mono">
                          Source: {selectedClaimForReview.originalWorkUrls.join(", ")}
                        </span>
                      )}
                    </div>
                    <div>
                      <span className="text-xs opacity-50 block mb-1">Work Description</span>
                      <p className="text-xs leading-relaxed">{selectedClaimForReview.originalWorkDescription}</p>
                    </div>
                  </div>
                </div>

                {/* Legal Declarations & Signature */}
                <div className="space-y-2">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--accent2)]">Legal Consent & Signature</h4>
                  <div className="bg-slate-900/30 border border-slate-800 p-4 rounded-2xl text-xs space-y-2">
                    <div className="flex items-start gap-2 text-slate-400">
                      <span className="text-green-500 font-bold mr-1">✓</span>
                      <span>Confirmed good faith belief of copyright infringement.</span>
                    </div>
                    <div className="flex items-start gap-2 text-slate-400">
                      <span className="text-green-500 font-bold mr-1">✓</span>
                      <span>Confirmed accuracy of report under penalty of perjury.</span>
                    </div>
                    <div className="border-t border-slate-800 pt-2 mt-2">
                      <span className="opacity-50 block">Electronic Signature</span>
                      <span className="font-mono text-sm font-bold text-slate-100 italic">
                        {selectedClaimForReview.signature}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Foot Actions */}
              <div className="p-6 border-t border-slate-800 flex justify-end gap-3 shrink-0">
                <button
                  className="admin-btn admin-btn-secondary"
                  onClick={() => setSelectedClaimForReview(null)}
                >
                  Cancel
                </button>
                <button
                  className="admin-btn admin-btn-primary"
                  onClick={() => handleAcknowledgeClaim(selectedClaimForReview.id)}
                  disabled={acknowledgingClaim}
                >
                  {acknowledgingClaim ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin mr-1.5" /> Acknowledging...
                    </>
                  ) : (
                    "Acknowledge Claim (24h SLA)"
                  )}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default AdminDashboard;
