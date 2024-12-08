package dev.shubham.labs.ecomm.kafka;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KafkaConsumerProps extends KafkaProps {

    private String consumerGroup;
    private int concurrency = 1;

}
