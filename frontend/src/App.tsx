import React, { useEffect, useState } from 'react';

  const App: React.FC = () => {
    return (
      <div className="App" data-testid="app-root">
        <h1>This is a placeholder</h1>
        <div style={{ marginTop: "30px" }}>
        <iframe width="560" height="315"
          src="https://www.youtube-nocookie.com/embed/rEq1Z0bjdwc"
          title="Hello There" frameBorder="0" allow = "autoplay; encrypted-media"
          allowFullScreen
        ></iframe>
      </div>
     </div>
   );
   };
export default App;