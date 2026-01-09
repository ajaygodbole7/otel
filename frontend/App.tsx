import { useEffect, useState } from "react";
import { useSSE } from "./SSEContext"

function App() {
  const eventSourceRef = useSSE();
  console.log('useSSE', eventSourceRef);
  const [listenerSet, setListenerSet] = useState<boolean>(false);

  useEffect(() => {
    console.log('eventSourceRef', eventSourceRef);
    if(eventSourceRef.current && !listenerSet) {
      setListenerSet(true);
      console.log('event source if');
      eventSourceRef.current.addEventListener('notification' ,(e: any) => {
        // const data = JSON.parse(e.data);
        console.log('SSE data:', e);
      });
    }
  }, [eventSourceRef]);

  return (
    <>hello</>
  )
}

export default App
