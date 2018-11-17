package com.example;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Data-класс для хранения натурального ключа
 */
public class DepCodeByDepJob {
    private String depCode;
    private String depJob;

    @Override
    public int hashCode() {
        return Objects.hash(depCode, depJob);
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == this)
            return true;

        if (obj == null || obj.getClass() != this.getClass())
            return false;

        DepCodeByDepJob depCodeByDepJob = (DepCodeByDepJob) obj;
        return this.depCode.equals(depCodeByDepJob.depCode) &&
                this.depJob.equals(depCodeByDepJob.depJob);
    }

    public void setDepCode(@Nullable String depCode) {
        this.depCode = depCode == null ? "" : depCode;
    }

    public void setDepJob(@Nullable String depJob) {
        this.depJob = depJob == null ? "" : depJob;
    }

    public String getDepCode() {
        return depCode;
    }

    public String getDepJob() {
        return depJob;
    }
}
