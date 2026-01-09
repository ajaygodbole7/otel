import { EventSourcePolyfill } from 'event-source-polyfill';
import {
  useEffect,
  useRef,
  createContext,
  FC,
  PropsWithChildren,
  useContext,
  RefObject,
  useState,
} from 'react';
 
const SSEContext = createContext<RefObject<EventSourcePolyfill | null> | null>(
  null
);
 
export const SSEContextProvider: FC<
  PropsWithChildren<{ notificationsHost: string }>
> = ({ children, notificationsHost }) => {
  const eventSourceRef = useRef<EventSourcePolyfill>(null);
  const [currentRef, setCurrentRef] =
    useState<RefObject<EventSourcePolyfill | null>>(eventSourceRef);
 
  let createConnectionCount = 0;
 
  const createSSEConnection = () => {
    createConnectionCount += 1;
    const eventSource = new EventSourcePolyfill(
      //'http://localhost:8080/connect',
      'http://localhost:4000/events',
      {
        withCredentials: true,
        heartbeatTimeout: 410000,
      }
    );
    eventSource.onopen = () => {
      createConnectionCount = 0;
    };

    eventSource.onmessage = () => {
      console.log('onmessage');
    }

    eventSource.onerror = () => {
      eventSource.close();
      eventSourceRef.current = null;
      const retryTimeout = 1000 ** (createConnectionCount * 0.5);
      setTimeout(() => {
        createSSEConnection();
      }, retryTimeout);
    };
    eventSourceRef.current = eventSource;
    setCurrentRef(eventSourceRef);
  };

  useEffect(() => {
    console.log(eventSourceRef);
  }, [eventSourceRef]);
 
  useEffect(() => {
    if (!eventSourceRef.current) {
      createSSEConnection();
      window.addEventListener('beforeunload', () => {
        if (eventSourceRef.current) {
          navigator.sendBeacon(`${notificationsHost}/v1/sse/disconnect`);
          eventSourceRef.current.close();
        }
      });
    }
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, []);
 
  return (
<SSEContext.Provider value={currentRef}>{children}</SSEContext.Provider>
  );
};
 
export const useSSE = () => {
  const context = useContext(SSEContext);
  if (!context) {
    throw new Error('useSSE must be used within an SSEContextProvider');
  }
  return context;
};