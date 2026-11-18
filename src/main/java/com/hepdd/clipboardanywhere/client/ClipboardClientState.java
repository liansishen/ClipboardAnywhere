package com.hepdd.clipboardanywhere.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import com.hepdd.clipboardanywhere.model.BindingView;
import com.hepdd.clipboardanywhere.model.ClipboardAction;
import com.hepdd.clipboardanywhere.model.ClipboardPageSnapshot;
import com.hepdd.clipboardanywhere.model.PlayerBindingSnapshot;
import com.hepdd.clipboardanywhere.network.NetworkCodec;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingStatePayload;
import com.hepdd.clipboardanywhere.network.NetworkCodec.BindingWireEntry;

public final class ClipboardClientState {

    public static final ClipboardClientState INSTANCE = new ClipboardClientState();
    public static final int OPTIMISTIC_EXPIRE_TICKS = 40;
    private static final int MAX_CACHED_PAGES_PER_BINDING = 16;
    private static final String[] EMPTY_TASKS = new String[ClipboardPageSnapshot.TASK_COUNT];
    private static final int[] EMPTY_STATES = new int[ClipboardPageSnapshot.TASK_COUNT];

    private PlayerBindingSnapshot authoritativeSnapshot = PlayerBindingSnapshot.EMPTY;
    private PlayerBindingSnapshot optimisticSnapshot = PlayerBindingSnapshot.EMPTY;
    private final Map<UUID, Map<Integer, ClipboardPageSnapshot>> pageCache = new HashMap<>();
    private final Queue<Notice> notices = new ArrayDeque<>();
    private boolean synced;
    private int nextMutationSeq;
    private int latestPendingSeq;
    private int optimisticExpireTicks;

    private ClipboardClientState() {}

    public PlayerBindingSnapshot getSnapshot() {
        return optimisticSnapshot;
    }

    public PlayerBindingSnapshot getAuthoritativeSnapshot() {
        return authoritativeSnapshot;
    }

    public boolean isSynced() {
        return synced;
    }

    public void update(PlayerBindingSnapshot snapshot) {
        applyPayload(BindingStatePayload.fullFromSnapshot(snapshot, latestPendingSeq));
    }

    public void applyPayload(BindingStatePayload payload) {
        BindingStatePayload incoming = payload == null ? BindingStatePayload.EMPTY : payload;
        List<BindingWireEntry> entries = incoming.getEntries();
        List<BindingView> mergedBindings = new ArrayList<>(entries.size());
        Set<UUID> currentIds = new HashSet<>(entries.size());
        for (BindingWireEntry entry : entries) {
            UUID id = entry.getId();
            currentIds.add(id);
            ClipboardPageSnapshot page;
            if (entry.hasPage()) {
                page = entry.getPage();
                cachePage(id, page);
            } else {
                BindingView previous = authoritativeSnapshot.get(id);
                page = previous != null ? previous.getSnapshot() : ClipboardPageSnapshot.EMPTY;
            }
            mergedBindings.add(new BindingView(id, entry.getDisplayName(), entry.getStatus(), page));
        }
        pageCache.keySet()
            .retainAll(currentIds);

        PlayerBindingSnapshot newAuthoritative = new PlayerBindingSnapshot(incoming.getActiveId(), mergedBindings);
        boolean wasAllDisconnected = this.authoritativeSnapshot.isAllDisconnected();
        this.authoritativeSnapshot = newAuthoritative;
        this.synced = true;
        if (!wasAllDisconnected && newAuthoritative.isAllDisconnected()) {
            addNotice(false, "message.clipboardanywhere.all_disconnected");
        }

        if (!isPendingAheadOf(incoming.getAckSeq())) {
            this.optimisticSnapshot = newAuthoritative;
            this.latestPendingSeq = 0;
            this.optimisticExpireTicks = 0;
        } else {
            this.optimisticSnapshot = mergePendingWithAuthoritative(newAuthoritative);
        }
    }

    public void tick() {
        if (latestPendingSeq != 0 && --optimisticExpireTicks <= 0) {
            optimisticSnapshot = authoritativeSnapshot;
            latestPendingSeq = 0;
            optimisticExpireTicks = 0;
        }
    }

    public int applyOptimisticSelect(UUID selectedId) {
        int seq = issueMutationSeq();
        if (selectedId != null && optimisticSnapshot.get(selectedId) != null) {
            optimisticSnapshot = new PlayerBindingSnapshot(selectedId, optimisticSnapshot.getBindings());
        }
        return seq;
    }

    public int applyOptimisticRename(UUID bindingId, String rawName) {
        int seq = issueMutationSeq();
        String sanitized = NetworkCodec.sanitizeName(rawName);
        BindingView current = optimisticSnapshot.get(bindingId);
        if (current == null || sanitized.isEmpty()
            || !current.getStatus()
                .isReadable()) {
            return seq;
        }
        replaceOptimisticBinding(
            new BindingView(current.getId(), sanitized, current.getStatus(), current.getSnapshot()));
        return seq;
    }

    public int applyOptimisticTaskText(UUID bindingId, int pageNumber, int row, String rawReplacementText) {
        int seq = issueMutationSeq();
        if (row < 0 || row >= ClipboardPageSnapshot.TASK_COUNT) return seq;
        String sanitized = NetworkCodec.sanitizeTaskText(rawReplacementText);
        if (sanitized == null) return seq;
        BindingView current = optimisticSnapshot.get(bindingId);
        if (current == null || !current.getStatus()
            .isReadable()) {
            return seq;
        }
        ClipboardPageSnapshot page = current.getSnapshot();
        if (page.getCurrentPage() != pageNumber) return seq;
        String[] tasks = page.getTasks();
        tasks[row] = sanitized;
        ClipboardPageSnapshot updatedPage = new ClipboardPageSnapshot(
            page.getCurrentPage(),
            page.getTotalPages(),
            page.getTitle(),
            tasks,
            page.getTaskStates(),
            page.getCapturedAt());
        cachePage(bindingId, updatedPage);
        replaceOptimisticBinding(
            new BindingView(current.getId(), current.getDisplayName(), current.getStatus(), updatedPage));
        return seq;
    }

    public int applyOptimisticAction(UUID bindingId, ClipboardAction action, int row) {
        int seq = issueMutationSeq();
        if (action == null) return seq;
        BindingView current = optimisticSnapshot.get(bindingId);
        if (current == null || !current.getStatus()
            .isReadable()) {
            return seq;
        }
        ClipboardPageSnapshot page = current.getSnapshot();
        ClipboardPageSnapshot updatedPage = null;
        switch (action) {
            case CYCLE_TASK:
                if (row >= 0 && row < ClipboardPageSnapshot.TASK_COUNT) {
                    int[] states = page.getTaskStates();
                    states[row] = (states[row] + 1) % 3;
                    updatedPage = new ClipboardPageSnapshot(
                        page.getCurrentPage(),
                        page.getTotalPages(),
                        page.getTitle(),
                        page.getTasks(),
                        states,
                        page.getCapturedAt());
                    cachePage(bindingId, updatedPage);
                }
                break;
            case PREVIOUS_PAGE:
                if (page.getCurrentPage() > 1) {
                    int targetPage = page.getCurrentPage() - 1;
                    updatedPage = resolveCachedOrPlaceholderPage(
                        bindingId,
                        targetPage,
                        page.getTotalPages(),
                        page.getCapturedAt());
                }
                break;
            case NEXT_PAGE:
                int nextPage = page.getCurrentPage() + 1;
                int newTotal = Math.max(page.getTotalPages(), nextPage);
                if (nextPage <= page.getTotalPages()) {
                    updatedPage = resolveCachedOrPlaceholderPage(bindingId, nextPage, newTotal, page.getCapturedAt());
                } else {
                    updatedPage = new ClipboardPageSnapshot(
                        nextPage,
                        newTotal,
                        "",
                        EMPTY_TASKS,
                        EMPTY_STATES,
                        page.getCapturedAt());
                    cachePage(bindingId, updatedPage);
                }
                break;
            default:
                break;
        }
        if (updatedPage != null) {
            replaceOptimisticBinding(
                new BindingView(current.getId(), current.getDisplayName(), current.getStatus(), updatedPage));
        }
        return seq;
    }

    public void addNotice(boolean success, String translationKey) {
        if (translationKey != null && !translationKey.isEmpty()) {
            notices.offer(new Notice(success, translationKey));
        }
    }

    public Notice pollNotice() {
        return notices.poll();
    }

    public void clear() {
        authoritativeSnapshot = PlayerBindingSnapshot.EMPTY;
        optimisticSnapshot = PlayerBindingSnapshot.EMPTY;
        pageCache.clear();
        notices.clear();
        synced = false;
        nextMutationSeq = 0;
        latestPendingSeq = 0;
        optimisticExpireTicks = 0;
    }

    private int issueMutationSeq() {
        int seq = ++nextMutationSeq;
        if (seq == 0) seq = ++nextMutationSeq;
        latestPendingSeq = seq;
        optimisticExpireTicks = OPTIMISTIC_EXPIRE_TICKS;
        return seq;
    }

    private boolean isPendingAheadOf(int ackSeq) {
        return latestPendingSeq != 0 && latestPendingSeq - ackSeq > 0;
    }

    private PlayerBindingSnapshot mergePendingWithAuthoritative(PlayerBindingSnapshot newAuthoritative) {
        UUID pendingActiveId = optimisticSnapshot.getActiveId();
        BindingView authForActive = newAuthoritative.get(pendingActiveId);
        if (authForActive == null) {
            latestPendingSeq = 0;
            optimisticExpireTicks = 0;
            return newAuthoritative;
        }
        List<BindingView> merged = new ArrayList<>(
            newAuthoritative.getBindings()
                .size());
        for (BindingView authView : newAuthoritative.getBindings()) {
            BindingView optView = optimisticSnapshot.get(authView.getId());
            if (optView != null && authView.getStatus()
                .isReadable()) {
                merged.add(
                    new BindingView(
                        authView.getId(),
                        optView.getDisplayName(),
                        authView.getStatus(),
                        optView.getSnapshot()));
            } else {
                merged.add(authView);
            }
        }
        UUID mergedActiveId = authForActive.getStatus()
            .isReadable() || pendingActiveId.equals(newAuthoritative.getActiveId()) ? pendingActiveId
                : newAuthoritative.getActiveId();
        return new PlayerBindingSnapshot(mergedActiveId, merged);
    }

    private void replaceOptimisticBinding(BindingView replacement) {
        List<BindingView> currentBindings = optimisticSnapshot.getBindings();
        List<BindingView> updated = new ArrayList<>(currentBindings.size());
        for (BindingView entry : currentBindings) {
            updated.add(
                entry.getId()
                    .equals(replacement.getId()) ? replacement : entry);
        }
        optimisticSnapshot = new PlayerBindingSnapshot(optimisticSnapshot.getActiveId(), updated);
    }

    private void cachePage(UUID bindingId, ClipboardPageSnapshot page) {
        if (bindingId == null || page == null) return;
        Map<Integer, ClipboardPageSnapshot> pages = pageCache.get(bindingId);
        if (pages == null) {
            pages = new LinkedHashMap<Integer, ClipboardPageSnapshot>(16, 0.75F, true) {

                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, ClipboardPageSnapshot> eldest) {
                    return size() > MAX_CACHED_PAGES_PER_BINDING;
                }
            };
            pageCache.put(bindingId, pages);
        }
        pages.put(Integer.valueOf(page.getCurrentPage()), page);
    }

    private ClipboardPageSnapshot resolveCachedOrPlaceholderPage(UUID bindingId, int pageNumber, int totalPages,
        long capturedAt) {
        Map<Integer, ClipboardPageSnapshot> pages = pageCache.get(bindingId);
        ClipboardPageSnapshot cached = pages == null ? null : pages.get(Integer.valueOf(pageNumber));
        if (cached != null) {
            if (cached.getTotalPages() == totalPages) return cached;
            return new ClipboardPageSnapshot(
                pageNumber,
                totalPages,
                cached.getTitle(),
                cached.getTasks(),
                cached.getTaskStates(),
                cached.getCapturedAt());
        }
        return new ClipboardPageSnapshot(pageNumber, totalPages, "", EMPTY_TASKS, EMPTY_STATES, capturedAt);
    }

    public static final class Notice {

        private final boolean success;
        private final String translationKey;

        private Notice(boolean success, String translationKey) {
            this.success = success;
            this.translationKey = translationKey;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }
}
