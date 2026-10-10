import GovernmentHeader from "../components/government/GovernmentHeader";
import GovernmentPostCard from "../components/government/GovernmentPostCard";

const DepartmentFeed = () => {
  return (
    <div className="space-y-4">

      <GovernmentHeader
        department="Ministry of Health & Family Welfare"
        description="Official announcements, advisories, and public notices."
      />

      <GovernmentPostCard
        title="New Vaccination Drive – Phase 4"
        content="Vaccination centers are now open across multiple districts. Citizens are advised to register on the official portal."
        time="1 hour ago"
      />

      <GovernmentPostCard
        title="Heatwave Advisory Issued"
        content="Due to rising temperatures, citizens are advised to stay hydrated and avoid outdoor activities during peak hours."
        time="3 hours ago"
      />

    </div>
  );
};

export default DepartmentFeed;
