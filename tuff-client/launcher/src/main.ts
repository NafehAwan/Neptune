import { mount } from "svelte";
import "@fontsource/silkscreen/400.css";
import "@fontsource/silkscreen/700.css";
import "@fontsource-variable/inter";
import "./app.css";
import App from "./App.svelte";

export default mount(App, { target: document.getElementById("app")! });
