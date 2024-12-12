package dev.shubham.labs.ecomm.kafka;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class KafkaConsumerProps extends KafkaProps {

    private String consumerGroup;
    private int concurrency = 1;
    private List<String> circuitBreakers;

}
