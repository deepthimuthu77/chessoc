"use client";

import Link from 'next/link';
import { useEffect, useRef } from 'react';
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
  const accountMenu = useRef<HTMLDetailsElement>(null);
  const accountTrigger = useRef<HTMLElement>(null);
  const closeAccount = () => { if (accountMenu.current) accountMenu.current.open = false; };
  useEffect(() => {
    function outside(event: PointerEvent) {
      if (!accountMenu.current?.contains(event.target as Node)) closeAccount();
    }
    function escape(event: KeyboardEvent) {
      if (event.key === 'Escape' && accountMenu.current?.open) { closeAccount(); accountTrigger.current?.focus(); }
    }
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
  }, []);
  return <>
    <a href="#main-content" className="skip-link">Skip to content</a>
    <nav className="site-nav" aria-label="Main navigation"><div className="site-nav-inner">
      <Link href="/" className="wordmark" aria-label="Chessoc home"><span className="brand-mark"><Icon name="knight" size={32} /></span>chessoc<span className="text-accent-400">.</span></Link>
      <div className="nav-links">{LINKS.map(link => <Link key={link.href} href={link.href} className="nav-link" aria-current={path === link.href ? 'page' : undefined}><Icon name={link.icon} size={15} />{link.label}</Link>)}</div>
      <div className="nav-account">
        {username && accountId ? <details ref={accountMenu} className="account-menu">
          <summary ref={accountTrigger} className="account-trigger" aria-label="Account">
            <span className="avatar" aria-hidden="true">{username.slice(0, 2).toUpperCase()}</span><span>Account</span><Icon name="chevron" size={14} className="account-chevron" />
          </summary>
          <div className="account-popover frosted-panel">
            <div className="account-identity"><strong>{username}</strong><span>{accountId.startsWith('guest-') ? 'Guest player' : 'Player account'}</span></div>
            <Link href={`/profile/${accountId}`} onClick={closeAccount} aria-label={`${username}'s profile`} aria-current={path === `/profile/${accountId}` ? 'page' : undefined}><Icon name="people" size={17} />Your profile</Link>
            <Link href="/settings" onClick={closeAccount} aria-current={path === '/settings' ? 'page' : undefined}><Icon name="settings" size={17} />Settings</Link>
            {accountId.startsWith('guest-') && <Link href="/upgrade" onClick={closeAccount}><Icon name="knight" size={17} />Create account</Link>}
            {onLogout && <button type="button" onClick={() => { closeAccount(); onLogout(); }}><Icon name="logout" size={17} />Sign out</button>}
          </div>
        </details> : <Link href="/login" className="button-secondary">Sign in</Link>}
      </div>
    </div></nav>
  </>;
}
