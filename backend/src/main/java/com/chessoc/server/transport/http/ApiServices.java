package com.chessoc.server.transport.http;

import com.chessoc.server.account.AccountRepository;
import com.chessoc.server.account.AccountService;
import com.chessoc.server.games.GamesRepository;
import com.chessoc.server.match.state.Lobby;
import com.chessoc.server.rating.LeaderboardService;
import com.chessoc.server.session.SessionRegistry;
import com.chessoc.server.session.TicketService;
import com.chessoc.server.session.TokenService;
import com.chessoc.server.social.FriendRepository;
import com.chessoc.server.social.Presence;
import java.util.function.LongSupplier;

/** Everything the HTTP API needs, bundled so the handler constructor stays readable. */
public record ApiServices(
        TokenService tokens,
        TicketService tickets,
        SessionRegistry sessions,
        Lobby lobby,
        AccountService accountService,
        AccountRepository accounts,
        LeaderboardService leaderboard,
        FriendRepository friends,
        GamesRepository games,
        Presence presence,
        long tokenTtlMs,
        LongSupplier clock) {
}
