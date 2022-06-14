/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: MIT-0
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.amazonaws.msk.samples;

import com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import com.amazonaws.services.schemaregistry.utils.ProtobufMessageType;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import java.util.Properties;
import ksql.Order;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.glue.model.DataFormat;

public class Producer {

  @Parameter(names = {"--help", "-h"}, help = true)
  private boolean help = false;
  @Parameter(names = {"--bootstrap-servers",
      "-bs"}, description = "kafka bootstrap servers endpoint")
  private String bootstrapServers;
  //    @Parameter(names={"--role-arn", "-rn"},description="ARN of a role in Glue Schema Registry AWS Account that producer will assume")
//    private String assumeRoleARN;
  @Parameter(names = {"--region",
      "-reg"}, description = "AWS Region where you want to point AWS STS client to e.g. us-east-1 Default is ap-southeast-2. Producer is only checking for the following regions ap-southeast-2, ap-southeast-1, us-east-1, us-east-2, us-west-1, us-west-2")
  private String regionName = "ap-south-1";
  @Parameter(names = {"--topic-name",
      "-topic"}, description = "Kafka topic name where you send the data records. Default is unicorn-ride-request-topic")
  private String topic = "demo-orders";
  @Parameter(names = {"--num-messages",
      "-nm"}, description = "number of messages you want producer to send. Default is 100")
  private String str_numOfMessages = "100";
//    @Parameter(names={"--iam-role-externalid", "-externalid"},description="ExternalId required for assuming IAM role in the schema registry account")
//    private String externalId;

  private final static Logger logger =
      LoggerFactory.getLogger(org.apache.kafka.clients.producer.Producer.class.getName());

  public static void main(String[] args) {
    Producer producer = new Producer();
    JCommander jc = JCommander.newBuilder()
        .addObject(producer)
        .build();
    jc.parse(args);
    if (producer.help) {
      jc.usage();
      return;
    }
    producer.startProducer();
  }

  /**
   * This method is used to start the Kafka producer.
   * It first uses the assumeGlueSchemaRegistryRole method
   * to assume an IAM role that is provided as a command
   * line argument. It then starts sending the Unicorn Ride Request
   * data records to the Kafka topic that is provided as a
   * command line argument, default is unicorn-ride-request-topic.
   *
   * @return Nothing
   */
  public void startProducer() {
        assumeGlueSchemaRegistryRole();
    KafkaProducer<String, Order.orders> producer =
        new KafkaProducer<>(getProducerConfig());
    int numberOfMessages = Integer.parseInt(str_numOfMessages);
    logger.info("Starting to send records...");
    for (int i = 0; i < numberOfMessages; i++) {
      Order.orders rideRequest = getRecord(i);
      String key = "key-" + i;
      ProducerRecord<String, Order.orders> record =
          new ProducerRecord<>(topic, key, rideRequest);
      producer.send(record, new ProducerCallback());
    }
  }

  /**
   * This method is creates a Unicorn Ride Request
   * object using Avro schema generated classes.
   * Values for Unicorn Ride Request object attributes
   * are hard-coded just for a demo purposes.
   *
   * @return demo.glue.schema.registry.avro.UnicornRideRequest object
   */
  public Order.orders getRecord(int requestId) {
            /*
             Initialise UnicornRideRequest object of
             class that is generated from AVRO Schema
             */
    Order.orders rideRequest = Order.orders.newBuilder()
        .setOrderid(requestId)
        .setItemid(Integer.toString(requestId))
        .setOrderunits(11.0)
        .setOrdertime(System.currentTimeMillis())
        .setAddress(Order.orders.addressMessage.newBuilder()
            .setCity("BLR")
            .setState("KA")
            .setZipcode(560087)
            .build())
        .build();
    logger.info(rideRequest.toString());
    return rideRequest;
  }

  /**
   * This method creates a Kafka producer configuration
   * properties object with relevant config values
   * and returns the configuration properties object to
   * the caller.
   *
   * @return java.util.Properties Kafka properties configuration properties
   */
  private Properties getProducerConfig() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServers);
    props.put(ProducerConfig.ACKS_CONFIG, "-1");
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "msk-cross-account-gsr-producer");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        GlueSchemaRegistryKafkaSerializer.class.getName());
    props.put(AWSSchemaRegistryConstants.DATA_FORMAT, DataFormat.PROTOBUF.name());
    props.put(AWSSchemaRegistryConstants.AWS_REGION, regionName);
    props.put(AWSSchemaRegistryConstants.REGISTRY_NAME, "Demo");
    props.put(AWSSchemaRegistryConstants.SCHEMA_NAME, "Orders");
    props.put(AWSSchemaRegistryConstants.PROTOBUF_MESSAGE_TYPE,
        ProtobufMessageType.POJO.getName());
    props.put(AWSSchemaRegistryConstants.SCHEMA_AUTO_REGISTRATION_SETTING,"true");
    return props;
  }

  /**
   * This method uses AWS STS to assume the
   * IAM Role that is passed as a command line
   * argument. It sets aws.accessKeyId, aws.secretAccessKey
   * and aws.sessionToken in the system properties.
   *
   * @return Nothing
   */
    public void assumeGlueSchemaRegistryRole() {
            System.setProperty("aws.accessKeyId", "xx");
            System.setProperty("aws.secretAccessKey", "xx");
            System.setProperty("aws.sessionToken", "xx");
    }
  private class ProducerCallback implements Callback {
    /**
     * This method is a callback method which gets
     * invoked when Kafka producer receives the messages delivery
     * acknowledgement.
     *
     * @return Nothing
     */
    @Override
    public void onCompletion(RecordMetadata recordMetaData, Exception e) {
      if (e == null) {
        logger.info("Received new metadata. \n" +
            "Topic:" + recordMetaData.topic() + "\n" +
            "Partition: " + recordMetaData.partition() + "\n" +
            "Offset: " + recordMetaData.offset() + "\n" +
            "Timestamp: " + recordMetaData.timestamp());
      } else {
        logger.info("There's been an error from the Producer side");
        e.printStackTrace();
      }
    }
  }
}
