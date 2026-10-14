import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;

public class FixtureTestLauncher {
  public static void main(String[] args) {
    var listener = new SummaryGeneratingListener();
    var launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);
    launcher.execute(LauncherDiscoveryRequestBuilder.request()
        .selectors(DiscoverySelectors.selectPackage("com.chalkak.backend.feedback.domain")).build());
    var summary = listener.getSummary();
    summary.printTo(new java.io.PrintWriter(System.out));
    summary.printFailuresTo(new java.io.PrintWriter(System.out));
    if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() != 0
        || summary.getTestsSkippedCount() != 0 || summary.getTestsSucceededCount() != summary.getTestsFoundCount()) {
      System.exit(1);
    }
  }
}
