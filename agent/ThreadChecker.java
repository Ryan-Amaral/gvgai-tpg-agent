package agent;

import java.util.TimerTask;

public class ThreadChecker extends TimerTask {

    @Override
    public void run() {
        // TODO Auto-generated method stub
        if(TpgAgentParallel.checkAllTeamsDone()) {
            //  do usual gen ending stuff
            TpgAgentParallel.endGeneration();
            if(TpgAgentParallel.gen < TpgAgentParallel.generations) { // start next generation
                TpgAgentParallel.tpg.selection();
                TpgAgentParallel.tpg.generateNewTeams();
                TpgAgentParallel.tpg.nextEpoch();
                TpgAgentParallel.startNewGeneration();
            }
        }
    }

}
