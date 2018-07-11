package agent;

import java.util.TimerTask;

public class ThreadChecker extends TimerTask {

    @Override
    public void run() {
        TpgAgentParallel.checkAllThreadsWorking();
    }

}