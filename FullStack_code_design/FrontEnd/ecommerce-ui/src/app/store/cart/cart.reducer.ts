// ╔══════════════════════════════════════════════════════════════╗
// ║           Cart State — Actions + Reducer + Selectors         ║
// ╚══════════════════════════════════════════════════════════════╝
import { createAction, createReducer, createFeatureSelector,
         createSelector, on, props } from '@ngrx/store';

// ── Types ─────────────────────────────────────────────────────────
export interface CartItem {
  productId:  string;
  name:       string;
  price:      number;
  quantity:   number;
  imageUrl:   string;
}

// ── State ─────────────────────────────────────────────────────────
export interface CartState {
  items:     CartItem[];
  isLoading: boolean;
  error:     string | null;
}

const initialState: CartState = { items: [], isLoading: false, error: null };

// ── Actions ───────────────────────────────────────────────────────
// DESIGN PATTERN: Command Pattern — each action = one intent
export const addToCart        = createAction('[Cart] Add Item',    props<{ item: CartItem }>());
export const removeFromCart   = createAction('[Cart] Remove Item', props<{ productId: string }>());
export const updateQuantity   = createAction('[Cart] Update Qty',  props<{ productId: string; quantity: number }>());
export const clearCart        = createAction('[Cart] Clear');
export const loadCartSuccess  = createAction('[Cart] Load Success', props<{ items: CartItem[] }>());

// ── Reducer ───────────────────────────────────────────────────────
export const cartReducer = createReducer(
  initialState,

  // Add item — if already in cart, increment quantity
  on(addToCart, (state, { item }) => {
    const exists = state.items.find(i => i.productId === item.productId);
    const items  = exists
      ? state.items.map(i => i.productId === item.productId
          ? { ...i, quantity: i.quantity + item.quantity }
          : i)
      : [...state.items, item];
    return { ...state, items };
  }),

  // Remove item from cart
  on(removeFromCart, (state, { productId }) => ({
    ...state,
    items: state.items.filter(i => i.productId !== productId),
  })),

  // Update quantity — remove if quantity reaches 0
  on(updateQuantity, (state, { productId, quantity }) => ({
    ...state,
    items: quantity <= 0
      ? state.items.filter(i => i.productId !== productId)
      : state.items.map(i => i.productId === productId ? { ...i, quantity } : i),
  })),

  on(clearCart, () => initialState),
  on(loadCartSuccess, (state, { items }) => ({ ...state, items })),
);

// ── Selectors ─────────────────────────────────────────────────────
const selectCartState   = createFeatureSelector<CartState>('cart');
export const selectCartItems = createSelector(selectCartState, s => s.items);

// Derived: total item count (sum of all quantities)
export const selectCartCount = createSelector(
  selectCartItems, items => items.reduce((sum, i) => sum + i.quantity, 0)
);

// Derived: total price
export const selectCartTotal = createSelector(
  selectCartItems, items => items.reduce((sum, i) => sum + i.price * i.quantity, 0)
);
