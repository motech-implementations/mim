package org.motechproject.nms.mobileacademy.domain;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;

/**
 * Tracks the completion record for a given calling number
 */
@Entity(tableName = "nms_ma_completion_records")
public class CompletionRecord {

    @Field
    private long callingNumber;

    @Field
    private int score;

    @Field
    private boolean sentNotification;

    @Field
    private int completionCount;

    public CompletionRecord(long callingNumber, int score) {
        this.callingNumber = callingNumber;
        this.score = score;
        sentNotification = false;
        completionCount = 1;
    }

    public CompletionRecord(long callingNumber, int score, boolean sentNotification, int completionCount) {
        this.callingNumber = callingNumber;
        this.score = score;
        this.sentNotification = sentNotification;
        this.completionCount = completionCount;
    }

    public long getCallingNumber() {
        return callingNumber;
    }

    public void setCallingNumber(long callingNumber) {
        this.callingNumber = callingNumber;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isSentNotification() {
        return sentNotification;
    }

    public void setSentNotification(boolean sentNotification) {
        this.sentNotification = sentNotification;
    }

    public int getCompletionCount() {
        return completionCount;
    }

    public void setCompletionCount(int completionCount) {
        this.completionCount = completionCount;
    }
}
