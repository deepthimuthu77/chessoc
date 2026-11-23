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
  play: { href: '/', icon: 'play', title: 'Back to the board', detail: 'Your next game, your way.' },
  watch: { href: '/watch', icon: 'watch', title: 'Take a front-row seat', detail: 'Live boards. Four games, one view.' },
  friends: { href: '/friends', icon: 'people', title: 'Better with company', detail: 'Find your people. Play together.' },
  history: { href: '/history', icon: 'history', title: 'Every move has a story', detail: 'Revisit your games and analyze positions.' },
  leaderboard: { href: '/leaderboard', icon: 'trophy', title: 'Find your next benchmark', detail: 'Explore the club rankings.' },
} satisfies Record<string, { href: string; icon: IconName; title: string; detail: string }>;

export function JourneyNav({ destinations = ['play', 'watch', 'history'] }: { destinations?: (keyof typeof DESTINATIONS)[] }) {
  return <aside className="journey" aria-label="Explore Chessoc"><p className="eyebrow">Make your next move</p><div className="journey-grid">{destinations.map(key => <FeatureLink key={key} {...DESTINATIONS[key]} />)}</div></aside>;
}
