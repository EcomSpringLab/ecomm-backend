package dev.shubham.labs.ecomm.kafka;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KafkaProducerProps extends KafkaProps {

    private boolean idempotence;

}
