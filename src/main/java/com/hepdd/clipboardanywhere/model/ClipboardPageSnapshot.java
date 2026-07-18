package com.hepdd.clipboardanywhere.model;

import java.util.Arrays;

import net.minecraft.nbt.NBTTagCompound;

public final class ClipboardPageSnapshot {

    public static final int TASK_COUNT = 9;
    public static final ClipboardPageSnapshot EMPTY = new ClipboardPageSnapshot(
        1,
        1,
        "",
        new String[TASK_COUNT],
        new int[TASK_COUNT],
        0L);

    private final int currentPage;
    private final int totalPages;
    private final String title;
    private final String[] tasks;
    private final int[] taskStates;
    private final long capturedAt;

    public ClipboardPageSnapshot(int currentPage, int totalPages, String title, String[] tasks, int[] taskStates,
        long capturedAt) {
        this.totalPages = Math.max(1, totalPages);
        this.currentPage = clamp(currentPage, 1, this.totalPages);
        this.title = title == null ? "" : title;
        this.tasks = normalizeTasks(tasks);
        this.taskStates = normalizeStates(taskStates);
        this.capturedAt = Math.max(0L, capturedAt);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public String getTitle() {
        return title;
    }

    public String getTask(int row) {
        return tasks[row];
    }

    public int getTaskState(int row) {
        return taskStates[row];
    }

    public String[] getTasks() {
        return tasks.clone();
    }

    public int[] getTaskStates() {
        return taskStates.clone();
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public boolean hasSameContent(ClipboardPageSnapshot other) {
        return other != null && currentPage == other.currentPage
            && totalPages == other.totalPages
            && title.equals(other.title)
            && Arrays.equals(tasks, other.tasks)
            && Arrays.equals(taskStates, other.taskStates);
    }

    public NBTTagCompound writeToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("currentPage", currentPage);
        tag.setInteger("totalPages", totalPages);
        tag.setString("title", title);
        tag.setIntArray("taskStates", taskStates);
        NBTTagCompound tasksTag = new NBTTagCompound();
        for (int row = 0; row < TASK_COUNT; row++) {
            tasksTag.setString("task" + (row + 1), tasks[row]);
        }
        tag.setTag("tasks", tasksTag);
        tag.setLong("capturedAt", capturedAt);
        return tag;
    }

    public static ClipboardPageSnapshot readFromNbt(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) {
            return EMPTY;
        }
        String[] tasks = new String[TASK_COUNT];
        NBTTagCompound tasksTag = tag.getCompoundTag("tasks");
        for (int row = 0; row < TASK_COUNT; row++) {
            tasks[row] = tasksTag.getString("task" + (row + 1));
        }
        return new ClipboardPageSnapshot(
            tag.getInteger("currentPage"),
            tag.getInteger("totalPages"),
            tag.getString("title"),
            tasks,
            tag.getIntArray("taskStates"),
            tag.getLong("capturedAt"));
    }

    private static String[] normalizeTasks(String[] source) {
        String[] result = new String[TASK_COUNT];
        for (int row = 0; row < TASK_COUNT; row++) {
            String value = source != null && row < source.length ? source[row] : null;
            result[row] = value == null ? "" : value;
        }
        return result;
    }

    private static int[] normalizeStates(int[] source) {
        int[] result = new int[TASK_COUNT];
        for (int row = 0; row < TASK_COUNT; row++) {
            int value = source != null && row < source.length ? source[row] : 0;
            result[row] = value >= 0 && value <= 2 ? value : 0;
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClipboardPageSnapshot)) return false;
        ClipboardPageSnapshot that = (ClipboardPageSnapshot) other;
        return currentPage == that.currentPage && totalPages == that.totalPages
            && capturedAt == that.capturedAt
            && title.equals(that.title)
            && Arrays.equals(tasks, that.tasks)
            && Arrays.equals(taskStates, that.taskStates);
    }

    @Override
    public int hashCode() {
        int result = currentPage;
        result = 31 * result + totalPages;
        result = 31 * result + title.hashCode();
        result = 31 * result + Arrays.hashCode(tasks);
        result = 31 * result + Arrays.hashCode(taskStates);
        result = 31 * result + (int) (capturedAt ^ capturedAt >>> 32);
        return result;
    }
}
