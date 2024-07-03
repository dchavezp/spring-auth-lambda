package com.dewitt;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

public class InfrastructureStack extends Stack {
    public InfrastructureStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfrastructureStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        final NetworkingStack networking = new NetworkingStack(this, "NetworkingStack");

        final DatabaseStack databaseStack = new DatabaseStack(this, "DatabaseStack", DatabaseStackProps.builder()
                .withVpc(networking.getVpc())
                .withApplicationSecurityGroup(networking.getApplicationSecurityGroup())
                .build());
    }
}
