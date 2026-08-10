// ╔══════════════════════════════════════════════════════════════╗
// ║           Product State — Reducer + Selectors                ║
// ╚══════════════════════════════════════════════════════════════╝
import { createAction, createReducer, createFeatureSelector,
         createSelector, on, props } from '@ngrx/store';

export interface Product {
  id:          string;
  name:        string;
  description: string;
  price:       number;
  imageUrl:    string;
  category:    string;
  stock:       number;
  rating:      number;
}

export interface ProductState {
  items:           Product[];
  selectedProduct: Product | null;
  isLoading:       boolean;
  error:           string | null;
  searchQuery:     string;
  totalCount:      number;
  currentPage:     number;
}

const initialState: ProductState = {
  items: [], selectedProduct: null, isLoading: false,
  error: null, searchQuery: '', totalCount: 0, currentPage: 0,
};

// Actions
export const loadProducts        = createAction('[Product] Load Products', props<{ page: number; query?: string }>());
export const loadProductsSuccess = createAction('[Product] Load Success', props<{ items: Product[]; total: number }>());
export const loadProductsFailure = createAction('[Product] Load Failure', props<{ error: string }>());
export const selectProduct       = createAction('[Product] Select', props<{ product: Product }>());
export const setSearchQuery      = createAction('[Product] Set Search', props<{ query: string }>());

export const productReducer = createReducer(
  initialState,
  on(loadProducts,        (state, { page, query }) => ({ ...state, isLoading: true, currentPage: page, searchQuery: query ?? '' })),
  on(loadProductsSuccess, (state, { items, total }) => ({ ...state, items, totalCount: total, isLoading: false })),
  on(loadProductsFailure, (state, { error }) => ({ ...state, isLoading: false, error })),
  on(selectProduct,       (state, { product }) => ({ ...state, selectedProduct: product })),
  on(setSearchQuery,      (state, { query }) => ({ ...state, searchQuery: query })),
);

// Selectors
const selectProductState      = createFeatureSelector<ProductState>('products');
export const selectAllProducts   = createSelector(selectProductState, s => s.items);
export const selectProductLoading = createSelector(selectProductState, s => s.isLoading);
export const selectSelectedProduct = createSelector(selectProductState, s => s.selectedProduct);
export const selectSearchQuery   = createSelector(selectProductState, s => s.searchQuery);
export const selectProductTotal  = createSelector(selectProductState, s => s.totalCount);
