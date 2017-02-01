// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.nvm;

import org.nlogo.agent.AgentIterator;
import org.nlogo.agent.AgentSet;
import org.nlogo.api.JobOwner;
import org.nlogo.api.LogoException;

public strictfp class ExclusiveJob
    extends Job {

  public ExclusiveJob(JobOwner owner,
                      AgentSet agentset,
                      Procedure topLevelProcedure,
                      int address,
                      Context parentContext,
                      Workspace workspace,
                      org.nlogo.api.MersenneTwisterFast random) {
    super(owner, agentset, topLevelProcedure, address, parentContext, workspace, random);
  }

  @Override
  boolean exclusive() {
    return true;
  }

  @Override
  public void step() {
    throw new UnsupportedOperationException();
  }

  void run()
      throws LogoException {
    // Note that this relies on shufflerators making a copy,
    // which might change in a future implementation. The
    // cases where it matters are those where something
    // happens that changes the agentset as we're iterating
    // through it, for example if we're iterating through
    // all turtles and one of them hatches; the hatched
    // turtle must not be returned by the shufflerator.
    // - ST 12/5/05, 3/15/06
    Context context = new Context(this, null, 0, null, workspace);
    context.agentBit = agentset.agentBit();
    for(AgentIterator it = agentset.shufflerator(random); it.hasNext();) {
      context.agent = it.next();
      if (parentContext != null) {
        context.activation = parentContext.activation;
      } else {
        // if the Job was created by Evaluator, then we may
        // have no parent context - ST 7/11/06
        context.activation =
            new Activation(topLevelProcedure, null, address);
      }
      context.ip = address;
      context.finished = false;
      context.runExclusive();
    }
  }

  public void runTest() {
    run();
  }

  // used by Evaluator.MyThunk
  public Object callReporterProcedure()
      throws LogoException {
    return new Context(this, agentset.iterator().next(), 0, null, workspace)
        .callReporterProcedure(new Activation(topLevelProcedure, null, 0));
  }

}
