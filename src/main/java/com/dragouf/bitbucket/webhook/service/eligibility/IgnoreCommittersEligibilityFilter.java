package com.dragouf.bitbucket.webhook.service.eligibility;

import com.dragouf.bitbucket.webhook.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.setting.Settings;
import com.dragouf.bitbucket.webhook.service.SettingsService;

/**
 * An EligibilityFilter that checks if the user that initiated the
 * RepositoryRefsChangedEvent is a user that is in the ignores list for the
 * hook configuration.
 *
 * @author Michael Irwin (mikesir87)
 */
public class IgnoreCommittersEligibilityFilter implements EligibilityFilter {

  private static final Logger logger = // CHECKSTYLE:logger
  LoggerFactory.getLogger(IgnoreCommittersEligibilityFilter.class);

  private SettingsService settingsService;

  /**
   * Constructs a new instance
   * @param settingsService Service to get the webhook settings
   */
  public IgnoreCommittersEligibilityFilter(
      SettingsService settingsService) {
    this.settingsService = settingsService;
  }

  @Override
  public boolean shouldDeliverNotification(EventContext event) {
    String eventUserName = event.getUsername();

    final Settings settings = settingsService.getSettings(
        event.getRepository());
    String ignoreCommitters = settings.getString(Notifier.IGNORE_COMMITTERS);
    if (ignoreCommitters == null || eventUserName == null)
      return true;

    for (String committer : ignoreCommitters.split(" ")) {
      if (committer.equalsIgnoreCase(eventUserName)) {
        logger.debug("Ignoring push event due to ignore committer {}",
            committer);
        return false;
      }
    }
    return true;
  }

}
