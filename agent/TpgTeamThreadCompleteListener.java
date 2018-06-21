package agent;

public class TpgTeamThreadCompleteListener implements ThreadCompleteListener {

    @Override
    public void notifyOfThreadComplete(Thread thread) {
        TpgAgentParallel.onTeamThreadComplete(thread);
    }

}
