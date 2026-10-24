import { mount } from "svelte";
import "@fontsource/geist/latin-400.css";
import "@fontsource/geist/latin-500.css";
import "@fontsource/geist/latin-600.css";
import "@fontsource/geist/latin-700.css";
import "@fontsource/geist/latin-800.css";
import "@fontsource/unbounded/latin-700.css";
import "@fontsource/unbounded/latin-800.css";
import "@fontsource/geist-mono/latin-400.css";
import "@fontsource/geist-mono/latin-500.css";
import "./app.css";
import App from "./App.svelte";

export default mount(App, { target: document.getElementById("app")! });
