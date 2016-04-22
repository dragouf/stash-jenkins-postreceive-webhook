package com.dragouf.bitbucket.webhook.service;

import java.util.Collection;

import com.atlassian.bitbucket.repository.RefChange;

/**
 * An evaluator that provides the branches that were affected by various
 * RefChanges.
 *
 * @author Michael Irwin (mikesir87)
 */
public interface BranchEvaluator {

  /**
   * Get the branches that were affected by the provided refChanges.
   * @param refChanges The changes due to a commit.
   * @return The branch names affected by the provided ref changes.
   */
  Iterable<String> getBranches(Collection<RefChange> refChanges);

}
