package agent;

import java.util.ArrayList;
import java.util.HashMap;

import javaclient.GymJavaHttpClient;
import javaclient.StepObject;
import sbbj_tpg.Team;

public class TpgTeamThread extends Thread {
    
    public Team team;
    public float score;
    public String lvl;
    public int id;
    public GymJavaHttpClient client;
    public boolean done = false;
    public boolean superDone = false;
    public boolean actuallyDoingShit = false; // need this because stupid thread freezing issue
    
    public TpgTeamThread(GymJavaHttpClient client, int id) {
        this.client = client;
        this.id = id;
    }
    
    public void renew(Team team) {
        this.team = team;
        done = false;
        superDone = false;
    }
    
    public void run() {
        
        while(true) {
            actuallyDoingShit = false;
            // hold back start of execution to avoid concurrency issues
            while(done) {
                
            }
            
            actuallyDoingShit = true; // java made me do this
            score = 0;
            for(int ep = 0; ep < TpgAgentParallel.eps; ep++) {
                float rwd = 0;
                //System.out.println("attempting to reset env");
                Object obs = client.resetEnv1(lvl); // reset the environment
                //System.out.println("successfully reset env");
                Boolean isDone = false; // whether current episode is done
                int action; // action for agent to do
                int stepC = 0; // stepCounter
                boolean isAutopilot = true; // for following sequence
                Object info = null;
                
                TpgAgentParallel.rewardMap.put(team, new HashMap<String,Float>());
                
                while(!isDone) { // iterate through environment
                    stepC++;
                    if(TpgAgentParallel.rep == 0 || TpgAgentParallel.stepSeqs.size() == 0 
                            || stepC > TpgAgentParallel.stepSeqs.get(ep).size()) { // finished with autopilot, do tpg
                        if(isAutopilot) {
                            isAutopilot = false;
                        }
                        action = (int) TpgAgentParallel.tpg.participate(team, TpgAgent.getFeatures(obs), TpgAgentParallel.curActions); // tpg chooses
                        
                        // record steps if rep 0, new sequence on first step
                        if(TpgAgentParallel.rep == 0) {
                            if(stepC == 1) {
                                TpgAgentParallel.stepSeqs.add(new ArrayList<Integer>());
                            }
                            TpgAgentParallel.stepSeqs.get(TpgAgentParallel.stepSeqs.size()-1).add(action);
                        }
                    }else{ // do autopilot, until finish sequence
                        action = TpgAgentParallel.stepSeqs.get(ep).get(stepC-1);
                    }
                    
                    StepObject step = client.stepEnv1(lvl, action, true, TpgAgentParallel.render);
                    obs = step.observation;
                    isDone = step.done;
                    if(!isAutopilot) {
                        rwd += step.reward;
                    }
                    info = step.info;
                    if(TpgAgentParallel.quickie || stepC >= TpgAgentParallel.maxSteps) {
                        break;
                    }
                } // episode done
                System.out.println("done episode");
                score += rwd;
                //TpgAgentParallel.tpg.reward(team, "ep" + Integer.toString(ep), rwd); // apply reward
                TpgAgentParallel.rewardMap.get(team).put("ep" + Integer.toString(ep), rwd);
                
                if(TpgAgentParallel.rep == 0) { // In rep 0 take sequences of losses
                    TpgAgentParallel.fixSequence(TpgAgentParallel.stepSeqs, info);
                }else if(TpgAgentParallel.rep == 1) { // record in win or lose this ep
                    if(!TpgAgent.didIWin(info)) {
                        TpgAgentParallel.epLosses[ep]++;
                    }
                }
            }// end ep loop
            score /= TpgAgentParallel.eps;
            done = true;
            if(TpgAgentParallel.threadIsDone(id)) {
                return;
            }
        }
    }
}
