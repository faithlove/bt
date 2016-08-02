package bt.example.cli;

import bt.Bt;
import bt.BtClient;
import bt.BtRuntime;
import bt.BtRuntimeBuilder;
import bt.data.DataAccessFactory;
import bt.data.file.FileSystemDataAccessFactory;
import bt.module.PeerExchangeModule;
import joptsimple.OptionException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class CliClient  {

    public static void main(String[] args) throws IOException {

        Options options;
        try {
            options = Options.parse(args);
        } catch (OptionException e) {
            Options.printHelp(System.out);
            return;
        }

        new CliClient().runWithOptions(options);
    }

    void runWithOptions(Options options) {

        DataAccessFactory dataAccess = new FileSystemDataAccessFactory(options.getTargetDirectory());

        BtRuntime runtime = BtRuntimeBuilder.builder()
                .module(new PeerExchangeModule())
                .build();

        BtClient client = Bt.client(runtime)
                .url(toUrl(options.getMetainfoFile()))
                .build(dataAccess);

        SessionStatePrinter printer = SessionStatePrinter.createKeyInputAwarePrinter(client.getSession().getTorrent());
        try {
            client.startAsync(state -> {
                printer.print(state);
                if (!options.shouldSeedAfterDownloaded() && state.getPiecesRemaining() == 0) {
                    client.stop();
                }
            }, 1000).thenRun(runtime::shutdown).join();

        } catch (Exception e) {
            // in case the start request to the tracker fails
            printer.shutdown();
            printAndShutdown(e);
        }
    }

    private static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Unexpected error", e);
        }
    }

    private static void printAndShutdown(Throwable e) {
        e.printStackTrace(System.out);
        System.out.flush();
        System.exit(1);
    }
}
