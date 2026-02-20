## Warmup

In the warmup phase, a single shared queue is filled by the message generator with `32,000`
messages. A `32-thread` thread pool is created, where each thread submits a task. In each task, one
endpoint is instantiated via the ConnectionManager, and that endpoint sends `1,000` messages polled
from the shared queue to the server. Once all `1,000` messages are sent, the endpoint is closed
directly without being returned to the connection pool.

## Main Phase

In the main phase, there is a map of `20` queues keyed by roomId `(1–20)`. There is one
ConnectionManager instance that manages all endpoint sessions via a per-room connection pool. The
message generator fills the queues with `500,000` messages, running on its own thread. Note that the
number of messages per queue is not necessarily equal across the 20 rooms. A `120-thread` thread
pool is created, with each thread assigned to a room (`6` workers per room). Each worker obtains an
endpoint from the ConnectionManager and polls its room's queue, and then sends messages to the
server until it dequeues a poison message. While the producer and consumer run concurrently, after
the generator finishes and all normal messages have been enqueued, the main thread sequentially
places one poison message per worker (`120` total) into the corresponding room queues.

## Retry Logic

Each worker sends messages via `sendWithRetry()`, which attempts up to `5` times per message. On a
successful response, the latency is recorded and the worker moves on to the next message. If the
response is null (timeout), the worker sleeps for an exponential backoff
period ($100ms * 2^{attempt}$)
and retries. If a transport exception occurs (e.g. connection dropped), the worker attempts to
reconnect via `connMgr.reconn()`, which closes the old endpoint and creates a new one, then sleeps
for
the same exponential backoff before retrying. If all `5` attempts are exhausted without success, the
message is recorded as a failure and the worker moves on.

## Exponential backoff

When a request fails, the server may be overloaded or the network is unstable. If all failed workers
retry immediately at a fixed interval, a large number of requests would flood in simultaneously,
further aggravating the server's load and creating a "retry storm." Exponential backoff doubles the
waiting time between each retry (`100ms, 200ms, 400ms, 800ms, 1600ms`), naturally spreading out
retry
requests over time, giving the server a window to recover, while also reducing wasted network
bandwidth from futile retries.