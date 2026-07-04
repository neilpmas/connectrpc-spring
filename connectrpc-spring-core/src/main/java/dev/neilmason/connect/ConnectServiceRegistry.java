package dev.neilmason.connect;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectServiceRegistry {

    private final Map<String, MethodEntry> methods = new ConcurrentHashMap<>();

    public ConnectServiceRegistry(List<BindableService> services) {
        for (BindableService service : services) {
            ServerServiceDefinition definition = service.bindService();
            for (ServerMethodDefinition<?, ?> methodDef : definition.getMethods()) {
                MethodDescriptor<?, ?> descriptor = methodDef.getMethodDescriptor();
                // fullMethodName is "package.Service/MethodName"
                methods.put(descriptor.getFullMethodName(), new MethodEntry(methodDef, descriptor));
            }
        }
    }

    public @Nullable MethodEntry lookup(String serviceName, String methodName) {
        return methods.get(serviceName + "/" + methodName);
    }

    // Holds the real ServerMethodDefinition so dispatch can invoke through its ServerCallHandler
    // (the same handler a real gRPC server would use), rather than reaching into the service
    // implementation via reflection.
    public record MethodEntry(
        ServerMethodDefinition<?, ?> methodDefinition,
        MethodDescriptor<?, ?> descriptor
    ) {}
}
