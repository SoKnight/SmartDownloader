# Smart Downloader

Small CLI app built for downloading algorithm testing purposes.

## Requirements

- `Java 21` or newer
- Internet connection (wow!)

## How to run
Just use standard command for running Java apps like this.

> [!NOTE]
> The `--enable-preview` JVM argument is mandatory because the algorithm uses the Structured Concurrency API (in preview).

```bash
java --enable-preview -jar smart-downloader-all.jar <max-simultaneous-downloads>
```

> [!TIP]
> The `<max-simultaneous-downloads>` argument defines the limit for parallel simultaneous download connections.<br>
> For example, algorithm will download up to 4 resources simultaneously if '4' will be passed as an argument.
