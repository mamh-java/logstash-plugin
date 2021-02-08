package jenkins.plugins.logstash.pipeline;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import hudson.model.Job;
import hudson.util.RunList;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.plugins.logstash.LogstashWriter;
import jenkins.plugins.logstash.Messages;

/**
 * Sends the tail of the log in a single event to a logstash indexer.
 * Pipeline counterpart of the LogstashNotifier.
 */
public class LogstashSendStep extends Step
{

  private int maxLines;
  private boolean failBuild;
  private int buildNumber;
  private String jobname;

  @DataBoundConstructor
  public LogstashSendStep(int maxLines, boolean failBuild, String jobname, int buildNumber)
  {
    this.maxLines = maxLines;
    this.failBuild = failBuild;
    this.jobname = jobname;
    this.buildNumber= buildNumber;
  }

  public int getMaxLines()
  {
    return maxLines;
  }

  public boolean isFailBuild()
  {
    return failBuild;
  }

  public int getBuildNumber() {
    return buildNumber;
  }

  public String getJobname() {
    return jobname;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception
  {
    return new Execution(context, maxLines, failBuild, jobname, buildNumber);
  }

  @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
  private static class Execution extends SynchronousNonBlockingStepExecution<Void>
  {

    private static final long serialVersionUID = 1L;

    private transient final int maxLines;
    private transient final int buildNumber;
    private transient final String jobname;
    private transient final boolean failBuild;

    Execution(StepContext context, int maxLines, boolean failBuild, String jobname, int buildNumber)
    {
      super(context);
      this.maxLines = maxLines;
      this.failBuild = failBuild;
      this.jobname = jobname;
      this.buildNumber = buildNumber;
    }

    @Override
    protected Void run() throws Exception
    {
      TaskListener listener = getContext().get(TaskListener.class);
      PrintStream errorStream = listener.getLogger();
      if(StringUtils.isNotEmpty(jobname)) {
        Job job = (Job) Jenkins.get().getItemByFullName(jobname);
        if(buildNumber > 0){
          Run run = job.getBuildByNumber(buildNumber);
          errorStream.println("logstash send to " + run.getDisplayName());
          LogstashWriter logstash = new LogstashWriter(run, errorStream, listener, run.getCharset());
          logstash.writeBuildLog(maxLines);
        } else {
          RunList<Run> runList = job.getBuilds();
          for (Run run : runList) {
            errorStream.println("logstash send to " + run.getDisplayName());
            LogstashWriter logstash = new LogstashWriter(run, errorStream, listener, run.getCharset());
            logstash.writeBuildLog(maxLines);
          }
        }
      } else {
        Run<?, ?> run = getContext().get(Run.class);
        errorStream.println("logstash send to " + run.getDisplayName());
        LogstashWriter logstash = new LogstashWriter(run, errorStream, listener, run.getCharset());
        logstash.writeBuildLog(maxLines);
      }
      return null;
    }

  }

  @Extension
  public static class DescriptorImpl extends StepDescriptor
  {

    /** {@inheritDoc} */
    @Override
    public String getDisplayName()
    {
      return Messages.DisplayName();
    }

    @Override
    public String getFunctionName()
    {
      return "logstashSend";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext()
    {
      Set<Class<?>> contexts = new HashSet<>();
      contexts.add(TaskListener.class);
      contexts.add(Run.class);
      return contexts;
    }
  }
}
