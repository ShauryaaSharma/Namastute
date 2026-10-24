import { Chip } from "@/components/ui/display";
import type { Rating } from "@/lib/api";
import { ratingText } from "@/lib/format";

// What others see next to a person's name: their rating ("92% 👍 · 12 ratings" or "New") and, once checked,
// the "ID verified" badge (Learning Notes 80), in the slot the design reserves after the name
export default function TrustChips({ rating }: { rating: Rating }) {
  return (
    <>
      <Chip variant="muted">{ratingText(rating)}</Chip>
      {rating.idVerified && <Chip variant="info">◆ ID verified</Chip>}
    </>
  );
}
