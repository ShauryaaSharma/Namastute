import SessionBox from "./session-box";

// Server Component: the static text is rendered on the server; SessionBox runs in the browser
export default function Home() {
  return (
    <main className="p-8">
      <h1 className="text-3xl font-bold">Namastute</h1>
      <p className="mt-2">Share a cab with people from your flight.</p>
      <SessionBox />
    </main>
  );
}
