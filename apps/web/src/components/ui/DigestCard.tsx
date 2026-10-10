import { ExternalLink, MessageCircle, PartyPopper, Siren } from "lucide-react";

export type DigestItem = {
  title: string;
  meta?: string;
};

type DigestCardProps = {
  city?: string;
  pincode?: string;
  victories?: DigestItem[];
  needsSupport?: DigestItem[];
  chats?: DigestItem[];
};

function shareDigest(city: string, pincode: string, victories: DigestItem[], needsSupport: DigestItem[], chats: DigestItem[]) {
  const lines = [
    `Namaste! Good morning from ${city}${pincode ? ` (${pincode})` : ""}`,
    "",
    "Local Victories Yesterday:",
    ...(victories.length ? victories.map((item) => `- ${item.title}${item.meta ? ` (${item.meta})` : ""}`) : ["- No new victories yet"]),
    "",
    "Needs Your Support Today:",
    ...(needsSupport.length ? needsSupport.map((item) => `- ${item.title}${item.meta ? ` (${item.meta})` : ""}`) : ["- Your area is calm right now"]),
    "",
    "Trending Mohalla Chats:",
    ...(chats.length ? chats.map((item) => `- ${item.title}`) : ["- Start the first useful update today"]),
  ];
  window.open(`https://wa.me/?text=${encodeURIComponent(lines.join("\n"))}`, "_blank", "noopener,noreferrer");
}

const DigestCard = ({
  city = "your city",
  pincode = "",
  victories = [],
  needsSupport = [],
  chats = [],
}: DigestCardProps) => (
  <div className="rounded-xl border border-base-300 bg-base-200 p-4 space-y-3">
    <div className="flex items-start justify-between gap-3">
      <div>
        <p className="text-sm font-black">Namaste! Morning Mohalla Update</p>
        <p className="text-xs opacity-60">{city}{pincode ? ` (${pincode})` : ""}</p>
      </div>
      <button
        type="button"
        onClick={() => shareDigest(city, pincode, victories, needsSupport, chats)}
        className="btn btn-xs bg-green-600 text-white border-none hover:bg-green-700"
      >
        <MessageCircle size={12} /> WhatsApp
      </button>
    </div>

    <div className="grid gap-2 sm:grid-cols-3">
      <DigestColumn icon={<PartyPopper size={14} />} title="Victories" items={victories} empty="No resolved updates yet" />
      <DigestColumn icon={<Siren size={14} />} title="Needs support" items={needsSupport} empty="No urgent issues right now" />
      <DigestColumn icon={<ExternalLink size={14} />} title="Chats" items={chats} empty="No trending chats yet" />
    </div>
  </div>
);

function DigestColumn({ icon, title, items, empty }: { icon: React.ReactNode; title: string; items: DigestItem[]; empty: string }) {
  return (
    <div className="rounded-lg bg-base-100/70 p-3">
      <p className="mb-2 flex items-center gap-1.5 text-[11px] font-black uppercase opacity-70">{icon}{title}</p>
      <div className="space-y-1.5">
        {(items.length ? items : [{ title: empty }]).slice(0, 2).map((item, index) => (
          <p key={`${item.title}-${index}`} className="text-xs leading-snug text-base-content/75">{item.title}</p>
        ))}
      </div>
    </div>
  );
}

export default DigestCard;
