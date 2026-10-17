# compat: TeaVM 0.16.0's ArrayBlockingQueue wait handler resumes the waiting green thread without restoring
# Thread.currentThread() (so the resumed thread runs as whichever thread ran last), resumes an interrupted
# waiter synchronously on the *interrupting* thread's stack, and clears the interrupt handler of the wrong thread.
s/^        WaitHandler(AsyncCallback<Boolean> callback) {$/        final TThread thread = TThread.currentThread();\n\n        WaitHandler(AsyncCallback<Boolean> callback) {/
s/^            callback.complete(false);$/            TThread.compatSetCurrentThread(thread);\n            callback.complete(false);/
s/^            callback.error(new TInterruptedException());$/            TThread.compatLater(() -> {\n                TThread.compatSetCurrentThread(thread);\n                callback.error(new TInterruptedException());\n            });/
s/^            callback.complete(true);$/            TThread.compatSetCurrentThread(thread);\n            callback.complete(true);/
s/^            TThread.currentThread().interruptHandler = null;$/            if (thread.interruptHandler == this) {\n                thread.interruptHandler = null;\n            }/
