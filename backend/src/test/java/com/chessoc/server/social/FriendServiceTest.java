package com.chessoc.server.social;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FriendServiceTest {

    private final InMemoryFriendRepository repo = new InMemoryFriendRepository();
    private final FriendService service = new FriendService(repo);

    @Test
    void requestThenAcceptCreatesMutualFriendship() {
        assertThat(service.request("a", "b")).isEqualTo(FriendService.Outcome.REQUESTED);
        assertThat(repo.incomingRequests("b")).containsExactly("a");
        assertThat(repo.areFriends("a", "b")).isFalse();

        assertThat(service.accept("b", "a")).isTrue();
        assertThat(repo.areFriends("a", "b")).isTrue();
        assertThat(repo.areFriends("b", "a")).isTrue();
        assertThat(repo.incomingRequests("b")).isEmpty();
    }

    @Test
    void reverseRequestAutoAccepts() {
        service.request("a", "b");
        assertThat(service.request("b", "a")).isEqualTo(FriendService.Outcome.FRIENDS);
        assertThat(repo.areFriends("a", "b")).isTrue();
        assertThat(repo.outgoingRequests("a")).isEmpty();
        assertThat(repo.outgoingRequests("b")).isEmpty();
    }

    @Test
    void declineDropsTheRequestWithoutFriending() {
        service.request("a", "b");
        service.decline("b", "a");
        assertThat(repo.incomingRequests("b")).isEmpty();
        assertThat(repo.areFriends("a", "b")).isFalse();
    }

    @Test
    void acceptWithNoRequestIsNoOp() {
        assertThat(service.accept("b", "a")).isFalse();
        assertThat(repo.areFriends("a", "b")).isFalse();
    }

    @Test
    void requestBetweenExistingFriendsReportsAlreadyFriends() {
        repo.add("a", "b");
        assertThat(service.request("a", "b")).isEqualTo(FriendService.Outcome.ALREADY_FRIENDS);
    }
}
