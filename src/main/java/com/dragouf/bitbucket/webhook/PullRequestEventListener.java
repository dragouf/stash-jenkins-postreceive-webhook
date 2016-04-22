package com.dragouf.bitbucket.webhook;

import com.atlassian.event.api.EventListener;
import com.atlassian.bitbucket.event.pull.PullRequestEvent;
import com.atlassian.bitbucket.event.pull.PullRequestOpenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestReopenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestRescopedEvent;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.dragouf.bitbucket.webhook.service.SettingsService;
import com.dragouf.bitbucket.webhook.service.eligibility.EligibilityFilterChain;
import com.dragouf.bitbucket.webhook.service.eligibility.EventContext;

/**
 * Event listener that listens to PullRequestRescopedEvent events.
 *
 * @author Michael Irwin (mikesir87)
 * @author Melvyn de Kort (lordmatanza)
 */
public class PullRequestEventListener {

  private final EligibilityFilterChain filterChain;
  private final Notifier notifier;
  private final SettingsService settingsService;
  private final PullRequestService pullRequestService;

  /**
   * Construct a new instance.
   * @param filterChain The filter chain to test for eligibility
   * @param notifier The notifier service
   * @param settingsService Service to be used to get the Settings
   */
  public PullRequestEventListener(EligibilityFilterChain filterChain,
                           Notifier notifier,
                           SettingsService settingsService,
                           PullRequestService pullRequestService) {
    this.filterChain = filterChain;
    this.notifier = notifier;
    this.settingsService = settingsService;
    this.pullRequestService = pullRequestService;
  }

  @EventListener
  public void onPullRequestRescoped(PullRequestRescopedEvent event) {
    final String previousHash = event.getPreviousFromHash();
    final String currentHash = event.getPullRequest().getFromRef().getLatestCommit();
    final Integer prId = event.getPullRequest().getToRef().getRepository().getId();

    //Using getToRef() here is required; pull requests are "scoped" to their target repository
    final Boolean canMerge = pullRequestService.canMerge(prId, event.getPullRequest().getId()).isConflicted();

    //Only trigger this if the pull request was rescoped on the from side, meaning new changes
    //were pushed. Doing this after every change on the to side will cause severe performance
    //degradation for your Stash server because it happens too often
    if (!previousHash.equals(currentHash) && canMerge) {
      //Notify Jenkins; the pull request refs have been updated
      handleEvent(event);
    }
  }

  /**
   * Event listener that is notified of pull request open events
   * @param event The pull request event
   */
  @EventListener
  public void onPullRequestOpened(PullRequestOpenedEvent event) {
    handleEvent(event);
  }

  /**
   * Event listener that is notified of pull request reopen events
   * @param event The pull request event
   */
  @EventListener
  public void onPullRequestReopened(PullRequestReopenedEvent event) {
    handleEvent(event);
  }

  /**
   * Actually handles the event that was triggered.
   * (Made protected to make unit testing easier)
   * @param event The event to be handled
   */
  protected void handleEvent(PullRequestEvent event) {
    if (settingsService.getSettings(event.getPullRequest().getToRef()
        .getRepository()) == null) {
      return;
    }

    String strRef = event.getPullRequest()
      .getFromRef()
      .toString()
      .replaceFirst(".*refs/heads/", "");

    String strSha1 = event.getPullRequest().getFromRef().getLatestCommit();

    EventContext context = new EventContext(event,
        event.getPullRequest().getToRef().getRepository(),
        event.getUser().getName());

    String prId = Long.toString(event.getPullRequest().getId());

    if (filterChain.shouldDeliverNotification(context))
      notifier.notifyBackground(context.getRepository(), strRef, strSha1, prId);
  }

}
