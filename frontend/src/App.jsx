import { BrowserRouter, Routes, Route } from "react-router-dom";
import Auctions from "./pages/Auctions";
import AuctionDetail from "./pages/AuctionDetail";
import Login from "./pages/Login";

function App() {
    return (
        <BrowserRouter>
            <main className="p-4">
                <Routes>
                    <Route path="/login" element={<Login />} />
                    <Route path="/auction/:id" element={<AuctionDetail />} />
                    <Route path="/login" element={<Login />} />
                </Routes>
            </main>
        </BrowserRouter>
    );
}

export default App;
