import Link from 'next/link';
import { Icon, type IconName } from './Icon';
import type { ReactNode } from 'react';

export function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return <header className="page-heading"><div><p className="eyebrow mb-3">{eyebrow}</p><h1 className="page-title">{title}</h1><p className="page-description">{description}</p></div>{action}</header>;
}

export function FeatureLink({ href, icon, title, detail }: { href: string; icon: IconName; title: string; detail: string }) {
  return <Link href={href} className="feature-link"><span className="icon-well"><Icon name={icon} size={19} /></span><span className="min-w-0"><span className="feature-link-title block">{title}</span><span className="feature-link-detail block">{detail}</span></span><Icon name="arrow" size={17} className="feature-arrow" /></Link>;
}

const DESTINATIONS = {
  play: { href: '/', icon: 'play', title: 'Play a game', detail: 'Choose your variant, time and opponent.' },
  watch: { href: '/watch', icon: 'watch', title: 'Watch live games', detail: 'Follow matches and manage your pinned games.' },
  friends: { href: '/friends', icon: 'people', title: 'Friends', detail: 'Add players and manage friend requests.' },
  history: { href: '/history', icon: 'history', title: 'Game history', detail: 'Replay your games and analyze positions.' },
  leaderboard: { href: '/leaderboard', icon: 'trophy', title: 'Rankings', detail: 'Compare ratings by variant and speed.' },
} satisfies Record<string, { href: string; icon: IconName; title: string; detail: string }>;

export function JourneyNav({ destinations = ['play', 'watch', 'history'] }: { destinations?: (keyof typeof DESTINATIONS)[] }) {
  return <aside className="journey" aria-label="Explore Chessoc"><p className="eyebrow">Make your next move</p><div className="journey-grid">{destinations.map(key => <FeatureLink key={key} {...DESTINATIONS[key]} />)}</div></aside>;
}
