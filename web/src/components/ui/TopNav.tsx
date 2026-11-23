"use client";

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Icon, type IconName } from './Icon';

const LINKS: { href: string; label: string; icon: IconName }[] = [
  { href: '/', label: 'Play', icon: 'board' },
  { href: '/watch', label: 'Watch', icon: 'watch' },
  { href: '/friends', label: 'Friends', icon: 'people' },
  { href: '/history', label: 'History', icon: 'history' },
  { href: '/leaderboard', label: 'Rankings', icon: 'trophy' },
];

export function TopNav({ username, accountId, onLogout }: { username?: string; accountId?: string; onLogout?: () => void }) {
  const path = usePathname();
  return <>
    <a href="#main-content" className="skip-link">Skip to content</a>
    <nav className="site-nav" aria-label="Main navigation"><div className="site-nav-inner">
      <Link href="/" className="wordmark" aria-label="Chessoc home"><span className="brand-mark"><Icon name="knight" size={32} /></span>chessoc<span className="text-accent-400">.</span></Link>
      <div className="nav-links">{LINKS.map(link => <Link key={link.href} href={link.href} className="nav-link" aria-current={path === link.href ? 'page' : undefined}><Icon name={link.icon} size={15} />{link.label}</Link>)}</div>
      <div className="nav-account">
        <Link href="/settings" className="nav-logout" aria-label="Settings" aria-current={path === '/settings' ? 'page' : undefined}><Icon name="settings" size={17} /></Link>
        {username && accountId ? <Link href={`/profile/${accountId}`} className="flex items-center gap-2" aria-label={`${username}'s profile`}><span className="avatar">{username.slice(0, 2).toUpperCase()}</span><span><span className="nav-account-name">{username}</span><span className="nav-account-label">{accountId.startsWith('guest-') ? 'Guest player' : 'Your profile'}</span></span></Link> : <Link href="/login" className="button-secondary">Sign in</Link>}
        {onLogout && <button type="button" onClick={onLogout} aria-label="Logout" title="Logout" className="nav-logout"><Icon name="logout" size={16} /></button>}
      </div>
    </div></nav>
  </>;
}
