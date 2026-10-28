# Reading plot sketches

The public API includes newly authored sketch-image extraction. It sends a resized sketch to the configured vision model and returns editable measurements. It does not call the private planning engine or generate floor plans.

Set `PLOTUS_SKETCH_MODEL_API_KEY` in the ignored Docker environment file. The defaults use Meta's `muse-spark-1.3` at `https://api.meta.ai/v1/chat/completions`. `PLOTUS_SKETCH_MODEL` and `PLOTUS_SKETCH_MODEL_ENDPOINT` can select a compatible HTTPS endpoint. Never put the key in the app, browser assets or Git.

An authenticated owner posts a JPEG or PNG to `/v1/projects/{id}/sketch`, then polls `/v1/projects/{id}/sketch/{job}`. Jobs are private to the account and project. At most two model calls run at once, with one active analysis per account and a bounded result cache. Model calls have a deadline; results expire after roughly ten minutes and do not survive a restart.

Images stay in memory during analysis. Plotus does not retain them on disk. The configured provider processes the image under its own data terms; requests disable optional output storage with `store: false`. The response is constrained to a JSON schema. Unknown dimensions remain null, and extracted geometry is validated against measurements. Reading a sketch never writes to the project: the user applies, reviews and explicitly saves the result.

Sources: [Meta image understanding](https://dev.meta.ai/docs/image-understanding), [Chat Completions schema](https://dev.meta.ai/docs/api-reference/chat-completions/schemas). The native photo picker and browser file picker are platform adapters; all app UI and review state remain shared Kotlin.
