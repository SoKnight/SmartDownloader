# Smart Downloader

Small CLI app built for downloading algorithm testing purposes.

## Requirements

- `Java 21` or newer
- Internet connection (wow!)

## How to run
Just use standard command for running Java apps like this.

> [!NOTE]
> The `--enable-preview` JVM argument is mandatory because the algorithm uses the Structured Concurrency API (in preview).

### Rev 0.4 or newer
```bash
java --enable-preview -jar smart-downloader-all.jar
```

> [!TIP]
> Starting from **rev 0.4**, the optimizing algorithm is used.<br>
> It tries to automatically suggest the optimal MSD value every 5 seconds with delay 10 seconds.

### Rev 0.3 or older
```bash
java --enable-preview -jar smart-downloader-all.jar <max-simultaneous-downloads>
```

> [!TIP]
> The `<max-simultaneous-downloads>` argument defines the limit for parallel simultaneous download connections.<br>
> For example, algorithm will download up to 4 resources simultaneously if '4' will be passed as an argument.
