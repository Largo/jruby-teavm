# compat: Thread.sleep() interrupted -> resume as the sleeping thread (TeaVM 0.16.0 resumes with whatever
# Thread.currentThread() happened to be).
s/^                EventQueue.offer(() -> callback.error(new TInterruptedException()));$/                EventQueue.offer(() -> {\n                    setCurrentThread(thread);\n                    callback.error(new TInterruptedException());\n                });/
s/^                Platform.postpone(() -> callback.error(new TInterruptedException()));$/                Platform.postpone(() -> {\n                    setCurrentThread(thread);\n                    callback.error(new TInterruptedException());\n                });/
# compat: on the JS backend Platform.startThread runs the new thread synchronously until it first suspends,
# leaving Thread.currentThread() pointing at the *new* thread in the starter; restore the starter.
s/^            Platform.startThread(this::runThread);$/            TThread starter = currentThread;\n            Platform.startThread(this::runThread);\n            setCurrentThread(starter);/
