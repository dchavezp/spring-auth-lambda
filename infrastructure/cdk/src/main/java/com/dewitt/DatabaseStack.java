package com.dewitt;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.HashMap;

public class DatabaseStack extends Construct{
    public DatabaseStack(final Construct construct, final String id, DatabaseStackProps stackProps){
        super(construct, id);

        DatabaseSecret dbSecret = createDatabaseSecret();

        ArrayList<ISecurityGroup> dbSecurityGroups = new ArrayList<>();
        dbSecurityGroups.add(createDatabaseSecurityGroup(stackProps.getVpc()));

        DatabaseInstance dbInstance = new DatabaseInstance(this, "auth-db-api", DatabaseInstanceProps.builder()
                .vpc(stackProps.getVpc())
                .databaseName("auth-db")
                .allowMajorVersionUpgrade(false)
                .backupRetention(Duration.days(0))
                .instanceIdentifier("auth-db-api")
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
                .securityGroups(dbSecurityGroups)
                .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder().version(PostgresEngineVersion.VER_16_3).build()))
                .instanceType(InstanceType.of(InstanceClass.T4G, InstanceSize.MICRO))
                .credentials(Credentials.fromSecret(dbSecret))
                .build());

        dbInstance.getConnections().allowFromAnyIpv4(Port.tcp(5432));
        dbInstance.getConnections().allowFrom(dbInstance.getConnections().getSecurityGroups().get(0), Port.tcp(5432));
        dbInstance.getConnections().allowFrom(stackProps.getApplicationSecurityGroup(), Port.tcp(5432));

        createDbSetupLambdaFunction(stackProps, dbInstance, dbSecret);

    }

    private SecurityGroup createDatabaseSecurityGroup(IVpc vpc) {
        SecurityGroup databaseSecurityGroup = SecurityGroup.Builder.create(this, "DatabaseSG")
                .securityGroupName("DatabaseSG")
                .allowAllOutbound(false)
                .vpc(vpc)
                .build();

        databaseSecurityGroup.addIngressRule(
                Peer.ipv4(vpc.getVpcCidrBlock()),
                Port.tcp(5432),
                "Allow Database Traffic from local network");

        return databaseSecurityGroup;
    }

    private DatabaseSecret createDatabaseSecret() {
        return DatabaseSecret.Builder
                .create(this, "postgres")
                .secretName("auth-api-db-secret")
                .username("postgres").build();
    }

    private void createDbSetupLambdaFunction(DatabaseStackProps props, DatabaseInstance database, DatabaseSecret secret) {
        ArrayList<ISecurityGroup> securityGroups = new ArrayList<>();
        securityGroups.add(props.getApplicationSecurityGroup());

        Function function = Function.Builder.create(this, "DBSetupLambdaFunction")
                .runtime(Runtime.JAVA_11)
                .memorySize(512)
                .timeout(Duration.seconds(29))
                .code(Code.fromAsset("../db-setup-lambda/target/db-setup.jar"))
                .handler("com.amazon.aws.DBSetupHandler::handleRequest")
                .vpc(props.getVpc())
                .securityGroups(securityGroups)
                .environment(new HashMap<>() {{
                    put("SECRET_NAME", secret.getSecretName());
                    put("DB_CONNECTION_URL", "jdbc:postgresql://" + database.getDbInstanceEndpointAddress() + ":5432/auth-db");
                    put("DB_USER", "postgres");
                }})
                .build();

        secret.grantRead(function);
    }
}
