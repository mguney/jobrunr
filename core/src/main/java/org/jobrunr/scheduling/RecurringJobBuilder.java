package org.jobrunr.scheduling;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.jobs.details.JobDetailsGenerator;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRunrJob;
import org.jobrunr.scheduling.carbonaware.CarbonAware;
import org.jobrunr.scheduling.cron.CronExpression;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.time.ZoneId.systemDefault;
import static java.util.Arrays.asList;
import static org.jobrunr.jobs.RecurringJob.CreatedBy.API;
import static org.jobrunr.utils.JobUtils.assertJobExists;

/**
 * This class is used to build a {@link RecurringJob} using a job lambda or a {@link JobRequest}.
 * <p>
 * You can use it as follows:
 * <h5>A job lambda example:</h5>
 * <pre>{@code
 *            MyService service = new MyService();
 *            jobScheduler.createRecurrently(aRecurringJob()
 *                                  .withCron("* * 0 * * *")
 *                                  .withDetails(() -> service.sendMail(toRequestParam, subjectRequestParam, bodyRequestParam));
 *       }</pre>
 * <h5>A JobRequest example:</h5>
 * <pre>{@code
 *          jobRequestScheduler.createRecurrently(aRecurringJob()
 *                                .withCron("* * 0 * * *")
 *                                .withJobRequest(new SendMailRequest(toRequestParam, subjectRequestParam, bodyRequestParam));
 *     }</pre>
 */
public class RecurringJobBuilder {

    private String jobId;
    private String jobName;
    private Integer retries;
    private List<String> labels;
    private JobRunrJob jobRunrJob;
    private JobRequest jobRequest;
    private Schedule schedule;
    private ZoneId zoneId;

    private RecurringJobBuilder() {
        // why: builder pattern
    }

    /**
     * Creates a new {@link RecurringJobBuilder} instance to create a {@link RecurringJob} using a Java 8 lambda.
     *
     * @return a new {@link RecurringJobBuilder} instance
     */
    public static RecurringJobBuilder aRecurringJob() {
        return new RecurringJobBuilder();
    }

    /**
     * Allows to set the id of the recurringJob. If a {@link RecurringJob} with that id already exists, JobRunr will not save it again.
     *
     * @param jobId the recurringJob of the recurringJob
     * @return the same builder instance which provides a fluent api
     */
    public RecurringJobBuilder withId(String jobId) {
        this.jobId = jobId;
        return this;
    }

    /**
     * Allows to set the name of the recurringJob for the dashboard.
     *
     * @param jobName the name of the recurringJob for the dashboard
     * @return the same builder instance which provides a fluent api
     */
    public RecurringJobBuilder withName(String jobName) {
        this.jobName = jobName;
        return this;
    }

    /**
     * Allows to specify number of times that the recurringJob should be retried.
     *
     * @param amountOfRetries the amount of times the recurringJob should be retried.
     * @return the same builder instance which provides a fluent api
     */
    public RecurringJobBuilder withAmountOfRetries(int amountOfRetries) {
        this.retries = amountOfRetries;
        return this;
    }

    /**
     * Allows to provide a set of labels to be shown in the dashboard.
     * A maximum of 3 labels can be provided per job. Each label has a max length of 45 characters.
     *
     * @param labels an array of labels to be added to the recurring job
     * @return the same builder instance which provides a fluent api
     */
    public RecurringJobBuilder withLabels(String... labels) {
        return withLabels(asList(labels));
    }

    /**
     * Allows to provide a set of labels to be shown in the dashboard.
     * A maximum of 3 labels can be provided per job. Each label has a max length of 45 characters.
     *
     * @param labels the list of labels to be added to the recurringJob
     * @return the same builder instance which provides a fluent api
     */
    public RecurringJobBuilder withLabels(List<String> labels) {
        this.labels = labels;
        return this;
    }

    /**
     * Allows to provide the job details by means of Java 8 lambda.
     *
     * @param jobLambda the lambda which defines the job
     * @return the same builder instance that can be given to the {@link JobScheduler#createRecurrently(RecurringJobBuilder)} method
     */
    public RecurringJobBuilder withDetails(JobLambda jobLambda) {
        if (this.jobRequest != null) {
            throw new IllegalArgumentException("withJobRequest() is already called, only 1 of [withDetails(), withJobRequest()] should be called.");
        }
        this.jobRunrJob = jobLambda;
        return this;
    }

    /**
     * Allows to provide the job details by means of Java 8 lambda. The IoC container will be used to resolve an actual instance of the requested service.
     *
     * @param ioCJobLambda the lambda which defines the job
     * @return the same builder instance that can be given to the {@link JobScheduler#createRecurrently(RecurringJobBuilder)} method
     */
    public <S> RecurringJobBuilder withDetails(IocJobLambda<S> ioCJobLambda) {
        if (this.jobRequest != null) {
            throw new IllegalArgumentException("withJobRequest() is already called, only 1 of [withDetails(), withJobRequest()] should be called.");
        }
        this.jobRunrJob = ioCJobLambda;
        return this;
    }

    /**
     * Allows to provide the job details by means of {@link JobRequest}.
     *
     * @param jobRequest the jobRequest which defines the job.
     * @return the same builder instance that can be given to the {@link JobRequestScheduler#createRecurrently(RecurringJobBuilder)} method
     */
    public RecurringJobBuilder withJobRequest(JobRequest jobRequest) {
        if (this.jobRunrJob != null) {
            throw new IllegalArgumentException("withJobLambda() is already called, only 1 of [withDetails(), withJobRequest()] should be called.");
        }
        this.jobRequest = jobRequest;
        return this;
    }

    /**
     * Allows to specify the {@link CronExpression} or a carbon aware schedule expression (see {@link CarbonAware}) that will be used to create the recurringJobs.
     *
     * @param cron the cron expression that will be used to create the Recurring Job.
     * @return the same builder instance which provides a fluent api
     * @see CarbonAware for more info on how to create a carbon aware schedule expression
     */
    public RecurringJobBuilder withCron(String cron) {
        return withScheduleExpression(cron);
    }

    /**
     * Allows to specify the interval that will be used between each instance of the recurring job. This interval mimicks the {@link java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}.
     *
     * @param duration the duration that will be used between each run of the Recurring Job
     * @return the same builder instance which provides a fluent api
     */
    public RecurringJobBuilder withInterval(Duration duration) {
        return withScheduleExpression(duration.toString());
    }

    /**
     * Allows to specify a {@link CronExpression} (preferably use {@link RecurringJobBuilder#withCron(String)}),
     * an ISO-8601 duration (preferably use {@link RecurringJobBuilder#withInterval(Duration)})
     * or a carbon aware schedule expression (see {@link CarbonAware}) that will be used to create the recurringJobs.
     *
     * <h5>Examples:</h5>
     *
     * <pre>{@code
     *      aRecurringJob()
     *          .withScheduleExpression(CarbonAware.dailyBefore(7))
     *          .withDetails(() -> service.doWork());
     * }</pre>
     *
     * <pre>{@code
     *      aRecurringJob()
     *          .withScheduleExpression("0 0 * * * [PT0H/PT7H]")
     *          .withDetails(() -> service.doWork());
     * }</pre>
     *
     * @param scheduleExpression the schedule expression that will be used to create the recurringJobs.
     * @return the same builder instance which provides a fluent api
     */
    public RecurringJobBuilder withScheduleExpression(String scheduleExpression) {
        if (this.schedule != null) {
            throw new IllegalArgumentException("A schedule has already been provided.");
        }
        this.schedule = ScheduleExpressionType.createScheduleFromString(scheduleExpression);
        return this;
    }

    /**
     * Allows to specify the zoneId that will be used to create the recurringJobs.
     * If no zoneId is set, the {@link ZoneId#systemDefault()} is used.
     *
     * @param zoneId the zoneId that will be used to create the recurringJobs.
     * @return the same builder instance which provides a fluent api
     */
    public RecurringJobBuilder withZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
        return this;
    }

    /**
     * Not publicly visible as it will be used by the {@link JobScheduler} only.
     *
     * @return the actual {@link Job} to create
     */
    protected RecurringJob build(JobDetailsGenerator jobDetailsGenerator) {
        if (jobRunrJob == null) {
            throw new IllegalArgumentException("JobLambda must be present.");
        }
        JobDetails jobDetails = jobDetailsGenerator.toJobDetails(jobRunrJob);
        return this.build(jobDetails);
    }

    /**
     * Not publicly visible as it will be used by the {@link JobRequestScheduler} only.
     *
     * @return the actual {@link RecurringJob} to create
     */
    protected RecurringJob build() {
        if (jobRequest == null) {
            throw new IllegalArgumentException("JobRequest must be present.");
        }

        JobDetails jobDetails = new JobDetails(jobRequest);
        assertJobExists(jobDetails);
        return this.build(jobDetails);
    }

    private RecurringJob build(JobDetails jobDetails) {
        if (schedule == null) {
            throw new IllegalArgumentException("A schedule must be present. Please call withCron() or withInterval() or withScheduleExpression().");
        }
        if (zoneId == null) {
            zoneId = systemDefault();
        }
        RecurringJob recurringJob = new RecurringJob(jobId, jobDetails, schedule, zoneId, API);
        setJobName(recurringJob);
        setAmountOfRetries(recurringJob);
        setLabels(recurringJob);
        return recurringJob;
    }

    private void setJobName(RecurringJob recurringJob) {
        if (jobName != null) {
            recurringJob.setJobName(jobName);
        }
    }

    private void setAmountOfRetries(RecurringJob recurringJob) {
        if (retries != null) {
            recurringJob.setAmountOfRetries(retries);
        }
    }

    private void setLabels(RecurringJob recurringJob) {
        if (labels != null) {
            recurringJob.setLabels(labels);
        }
    }
}
