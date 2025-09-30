package com.prod_mas.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class Scheduler extends Agent {
    private boolean taskSent = false;
    private String pendingTask = String.format("http://%s:8080/files/main_100.exe", System.getProperty("MAIN_HOST"));
    private final Map<AID, BigDecimal> workloadMap = new HashMap<>();
    private int repliesExpected = 0;

    @Override
    protected void setup() {
        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                if (taskSent || pendingTask == null) {
                    stop();
                    return;
                }

                System.out.println(pendingTask);

                try {
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("model-trainer");
                    template.addServices(sd);

                    DFAgentDescription[] result = DFService.search(myAgent, template);

                    if (result.length == 0) {
                        System.out.println("No workers available!");
                        return;
                    }

                    workloadMap.clear();
                    repliesExpected = result.length;

                    for (DFAgentDescription desc : result) {
                        AID worker = desc.getName();
                        ACLMessage req = new ACLMessage(ACLMessage.QUERY_IF);
                        req.addReceiver(worker);
                        req.setContent("Workload?");
                        send(req);
                    }

                    System.out.println("Sent workload requests to " + repliesExpected + " workers.");

                } catch (FIPAException fe) {
                    fe.printStackTrace();
                }
            }
        });

        // 2) Collect replies asynchronously
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.INFORM) {
                        try {
                            var load = new BigDecimal(msg.getContent());
                            workloadMap.put(msg.getSender(), load);
                            System.out.println("Worker " + msg.getSender().getLocalName() + " load=" + load);

                            // once all replies collected → assign
                            if (workloadMap.size() == repliesExpected && !workloadMap.isEmpty() && pendingTask != null) {
                                AID chosen = workloadMap.entrySet().stream()
                                        .min(Comparator.comparing(Map.Entry::getValue))
                                        .map(Map.Entry::getKey)
                                        .orElse(null);

                                if (chosen != null) {
                                    ACLMessage assign = new ACLMessage(ACLMessage.REQUEST);
                                    assign.addReceiver(chosen);
                                    assign.setContent(pendingTask);
                                    send(assign);

                                    System.out.println("Assigned task to " + chosen.getLocalName());
                                    taskSent = true;
                                    pendingTask = null;
                                }
                            }

                        } catch (NumberFormatException ignored) {
                            System.out.println("Invalid workload reply: " + msg.getContent());
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }
}