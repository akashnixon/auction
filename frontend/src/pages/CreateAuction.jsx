import { useContext, useState } from "react";
import { useNavigate } from "react-router-dom";
import { auctionApi, parseApiError } from "../api/api";
import { AuthContext } from "../context/AuthContext";
import defaultAuctionImage from "../assets/default-auction.svg";

const launchChecklist = [
    "Auction timing is enforced on the server, not in the browser.",
    "This demo uses a 30-second live window per listing.",
    "Winner selection follows highest bid, then earliest server arrival.",
];
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];

function centerCropToSquare(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
            const image = new Image();
            image.onload = () => {
                const size = Math.min(image.width, image.height);
                const offsetX = (image.width - size) / 2;
                const offsetY = (image.height - size) / 2;
                const canvas = document.createElement("canvas");
                const outputSize = Math.min(size, 1200);
                canvas.width = outputSize;
                canvas.height = outputSize;
                const context = canvas.getContext("2d");

                if (!context) {
                    reject(new Error("Image preview could not be prepared"));
                    return;
                }

                context.drawImage(
                    image,
                    offsetX,
                    offsetY,
                    size,
                    size,
                    0,
                    0,
                    outputSize,
                    outputSize
                );

                resolve(canvas.toDataURL("image/jpeg", 0.92));
            };
            image.onerror = () => reject(new Error("Unsupported image file"));
            image.src = String(reader.result);
        };
        reader.onerror = () => reject(new Error("Unable to read selected image"));
        reader.readAsDataURL(file);
    });
}

export default function CreateAuction() {
    const { user } = useContext(AuthContext);
    const navigate = useNavigate();
    const [itemName, setItemName] = useState("");
    const [startingPrice, setStartingPrice] = useState("0");
    const [imageDataUrl, setImageDataUrl] = useState("");
    const [error, setError] = useState("");
    const [imageError, setImageError] = useState("");
    const [loading, setLoading] = useState(false);
    const [processingImage, setProcessingImage] = useState(false);

    const handleImageChange = async (event) => {
        const file = event.target.files?.[0];
        setImageError("");

        if (!file) {
            setImageDataUrl("");
            return;
        }

        if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
            setImageError("Use a JPG, PNG, or WebP image.");
            return;
        }

        if (file.size > MAX_IMAGE_BYTES) {
            setImageError("Image must be 2 MB or smaller.");
            return;
        }

        try {
            setProcessingImage(true);
            const cropped = await centerCropToSquare(file);
            setImageDataUrl(cropped);
        } catch (uploadError) {
            setImageError(uploadError.message || "Unable to prepare this image.");
        } finally {
            setProcessingImage(false);
        }
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setLoading(true);
        setError("");

        try {
            const response = await auctionApi.post("/auctions", {
                itemName,
                sellerId: user.userId,
                imageDataUrl: imageDataUrl || null,
                startingPrice: Number(startingPrice),
            });
            navigate(`/auction/${response.data.auctionId}`);
        } catch (requestError) {
            setError(parseApiError(requestError, "Unable to create auction"));
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className="detail-grid create-grid">
            <section className="panel">
                <div className="section-heading">
                    <p className="eyebrow">Seller Launch</p>
                    <h1>Create Auction</h1>
                    <p>Advertise a new item and open a server-managed live auction window.</p>
                </div>

                <form className="form-stack" onSubmit={handleSubmit}>
                    <label className="form-field">
                        <span>Item Name</span>
                        <input
                            className="form-input"
                            value={itemName}
                            onChange={(event) => setItemName(event.target.value)}
                            placeholder="Vintage watch, gaming console, signed jersey..."
                        />
                    </label>

                    <label className="form-field">
                        <span>Starting Price</span>
                        <input
                            className="form-input"
                            type="number"
                            min="0"
                            step="0.01"
                            value={startingPrice}
                            onChange={(event) => setStartingPrice(event.target.value)}
                            placeholder="0.00"
                        />
                    </label>

                    <label className="form-field">
                        <span>Auction Image</span>
                        <input
                            className="form-input"
                            type="file"
                            accept="image/*"
                            onChange={handleImageChange}
                        />
                    </label>
                    <p className="muted-text upload-hint">
                        Images are validated, resized, and center-cropped for a clean card layout.
                    </p>

                    <div className="image-preview-card">
                        <img
                            src={imageDataUrl || defaultAuctionImage}
                            alt="Auction preview"
                            className="image-preview"
                        />
                    </div>

                    {processingImage ? (
                        <p className="muted-text subtle-refresh">Preparing image preview...</p>
                    ) : null}
                    {imageError ? <p className="status-error">{imageError}</p> : null}
                    {error ? <p className="status-error">{error}</p> : null}

                    <button
                        className="primary-button"
                        disabled={
                            loading ||
                            processingImage ||
                            !itemName.trim() ||
                            startingPrice === "" ||
                            Number(startingPrice) < 0
                        }
                        type="submit"
                    >
                        {loading ? "Creating..." : "Create Auction"}
                    </button>
                </form>
            </section>

            <section className="detail-card insights-card">
                <p className="eyebrow">Before You Launch</p>
                <h2>Operational Notes</h2>
                <div className="stack-lg">
                    {launchChecklist.map((item) => (
                        <div key={item} className="insight-row">
                            <span className="insight-dot" />
                            <p>{item}</p>
                        </div>
                    ))}
                </div>
            </section>
        </section>
    );
}
